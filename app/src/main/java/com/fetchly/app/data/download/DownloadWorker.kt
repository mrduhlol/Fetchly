package com.fetchly.app.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fetchly.app.FetchlyApp
import com.fetchly.app.data.engine.DownloadHook
import com.fetchly.app.data.engine.DownloadProgress
import com.fetchly.app.data.engine.EngineDownload
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import com.fetchly.app.domain.resolve.OptionMatcher
import com.fetchly.app.domain.util.Filenames
import com.fetchly.app.domain.util.FormatSize
import com.fetchly.app.domain.util.MediaSniffer
import com.fetchly.app.domain.util.MimeTypes
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

/**
 * Runs one download, from the stored history row to a file in the user's
 * media library.
 *
 * Shape of a run: streams are fetched by yt-dlp (or straight over OkHttp for
 * probed direct files), assembled with FFmpegKit when more than one part
 * arrived, verified against real magic bytes, and published through
 * MediaStore. A 403/410-style refusal re-resolves the link once before the
 * user ever hears about it; network failures return `Result.retry()` a
 * bounded number of times. The row is the source of truth throughout:
 * COMPLETED and CANCELLED are never overwritten, and every failure keeps both
 * the honest sentence and the raw engine text.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val graph get() = (applicationContext as FetchlyApp).graph

    /** Stage text and numbers shown to WorkManager and the notification. */
    private val stage = AtomicReference<String?>(null)
    private val latest = AtomicReference(DownloadProgress(0L, 0L, 0.0))

    @Volatile private var activeHook: DownloadHook? = null
    @Volatile private var activeCall: Call? = null

    override suspend fun doWork(): Result {
        // CoroutineWorker cancels this coroutine when the work is stopped
        // (onStopped is final), so react to the job's own cancellation:
        // stop the Python download at its next chunk, abort the OkHttp body,
        // cut any ffmpeg merge short. Guarded by cause so a normal finish
        // never touches another download's session.
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) cancelActiveWork()
        }
        val id = inputData.getString(KEY_DOWNLOAD_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val history = graph.history

        val loaded = history.getById(id) ?: return Result.success()
        if (loaded.status == DownloadStatus.COMPLETED || loaded.status == DownloadStatus.CANCELLED) {
            return Result.success()
        }
        history.setStatus(id, DownloadStatus.RUNNING, null, null, System.currentTimeMillis())

        // A cancel that raced the RUNNING write must still win.
        val entry = history.getById(id) ?: return Result.success()
        if (entry.status == DownloadStatus.CANCELLED) return Result.success()

        val scratch = TempFiles.dir(applicationContext)
        scratch.mkdirs()
        val prefix = Filenames.tempPrefix(id)

        try {
            setForeground(foregroundInfo(entry))
            val outcome = try {
                withProgressPump(entry) { runPipeline(entry, scratch) }
            } catch (exc: CancellationException) {
                throw exc
            } catch (exc: Exception) {
                PipelineOutcome.Failed(
                    message = DownloadErrors.textFor(exc.message),
                    raw = exc.message,
                    network = false,
                )
            }

            return when (outcome) {
                is PipelineOutcome.Completed -> {
                    history.setCompleted(
                        id, outcome.uri, outcome.name, outcome.path, outcome.size,
                        System.currentTimeMillis(),
                    )
                    notifyCompleted(entry, outcome)
                    Result.success()
                }

                PipelineOutcome.Cancelled -> {
                    markCancelled(id)
                    Result.success()
                }

                is PipelineOutcome.Failed -> finishFailed(entry, outcome)
            }
        } finally {
            cleanupTemps(scratch, prefix)
        }
    }

    // ------------------------------------------------------------ pipeline

    private suspend fun runPipeline(entry: HistoryEntry, scratch: File): PipelineOutcome {
        val first = optionFrom(entry)
        val (option, files) = when (val attempt = downloadStreams(entry, first, scratch)) {
            is StreamsOutcome.Ok -> first to attempt.files
            StreamsOutcome.Cancelled -> return PipelineOutcome.Cancelled
            is StreamsOutcome.Expired -> {
                val fresh = refreshOption(entry)
                    ?: return PipelineOutcome.Failed(
                        DownloadErrors.textFor(attempt.raw), attempt.raw, network = false,
                    )
                when (val second = downloadStreams(entry, fresh, scratch)) {
                    is StreamsOutcome.Ok -> fresh to second.files
                    StreamsOutcome.Cancelled -> return PipelineOutcome.Cancelled
                    is StreamsOutcome.Expired -> return PipelineOutcome.Failed(
                        DownloadErrors.textFor(second.raw), second.raw, network = false,
                    )
                    is StreamsOutcome.Failed -> return PipelineOutcome.Failed(
                        second.message, second.raw, second.network,
                    )
                }
            }
            is StreamsOutcome.Failed -> return PipelineOutcome.Failed(
                attempt.message, attempt.raw, attempt.network,
            )
        }

        stage.set("Merging…")
        val artifact = when (
            val assembled = assemble(option, files, scratch, Filenames.tempPrefix(entry.id))
        ) {
            is AssembleOutcome.Ok -> assembled.file
            AssembleOutcome.Cancelled -> return PipelineOutcome.Cancelled
            is AssembleOutcome.Failed -> return PipelineOutcome.Failed(
                assembled.message, assembled.raw, network = false,
            )
        }

        verify(artifact, option, entry)?.let { problem ->
            return PipelineOutcome.Failed(problem.message, problem.raw, network = false)
        }

        stage.set("Saving…")
        val name = Filenames.displayName(entry.title, option.label, option.container)
        val mime = MimeTypes.forContainer(option.container, audio = option.kind == QualityKind.AUDIO)
        return when (
            val published = MediaStorePublisher.publish(
                applicationContext, artifact, name, mime, entry.mediaType,
            )
        ) {
            is PublishResult.Ok -> PipelineOutcome.Completed(
                uri = published.uri,
                name = name,
                mime = mime,
                path = artifact.absolutePath,
                size = artifact.length(),
            )
            is PublishResult.Failed -> PipelineOutcome.Failed(
                "The download finished but couldn't be saved to your media library.",
                published.raw,
                network = false,
            )
        }
    }

    // ------------------------------------------------------------ streams

    private sealed interface StreamsOutcome {
        data class Ok(val files: List<File>) : StreamsOutcome
        data object Cancelled : StreamsOutcome
        data class Expired(val raw: String) : StreamsOutcome
        data class Failed(val message: String, val raw: String?, val network: Boolean) : StreamsOutcome
    }

    private sealed interface StreamResult {
        data class Ok(val file: File) : StreamResult
        data object Cancelled : StreamResult
        data class Expired(val raw: String) : StreamResult
        data class Failed(val message: String, val raw: String?, val network: Boolean) : StreamResult
    }

    private suspend fun downloadStreams(
        entry: HistoryEntry,
        option: QualityOption,
        scratch: File,
    ): StreamsOutcome {
        val prefix = Filenames.tempPrefix(entry.id)
        val files = mutableListOf<File>()
        for ((index, stream) in option.streams.withIndex()) {
            val audioOnly = stream.vcodec == "none" && stream.acodec != "none"
            stage.set(
                when {
                    option.kind == QualityKind.FILE -> "Downloading…"
                    audioOnly -> "Downloading audio…"
                    else -> "Downloading video…"
                }
            )
            latest.set(DownloadProgress(0L, stream.sizeBytes ?: 0L, 0.0))
            val stem = when {
                option.streams.size == 1 && option.kind == QualityKind.FILE -> "${prefix}_file"
                audioOnly -> "${prefix}_a"
                else -> "${prefix}_v"
            }
            when (val result = fetchStream(entry, stream, scratch, stem)) {
                is StreamResult.Ok -> files += result.file
                StreamResult.Cancelled -> return StreamsOutcome.Cancelled
                is StreamResult.Expired -> return StreamsOutcome.Expired(result.raw)
                is StreamResult.Failed -> return StreamsOutcome.Failed(
                    result.message, result.raw, result.network,
                )
            }
        }
        return StreamsOutcome.Ok(files)
    }

    private suspend fun fetchStream(
        entry: HistoryEntry,
        stream: StreamRef,
        scratch: File,
        stem: String,
    ): StreamResult {
        // A probed direct file is plain bytes at a URL: fetch it ourselves so
        // an HTML sign-in page can never pass for media (magic bytes are
        // checked before saving).
        if (entry.kind == QualityKind.FILE && stream.formatId == DIRECT_FORMAT_ID) {
            return downloadDirect(stream, File(scratch, "$stem.${stream.ext.ifEmpty { "bin" }}"))
        }
        val hook = DownloadHook { latest.set(it) }
        activeHook = hook
        return try {
            when (val outcome = graph.engine.download(
                entry.sourceUrl, stream.formatId, scratch, stem, hook,
            )) {
                is EngineDownload.Ok -> StreamResult.Ok(outcome.file)
                EngineDownload.Cancelled -> StreamResult.Cancelled
                is EngineDownload.Failed -> classifyEngineFailure(outcome.message)
            }
        } finally {
            activeHook = null
        }
    }

    private fun classifyEngineFailure(raw: String): StreamResult =
        if (DownloadErrors.isExpiredLink(raw)) {
            StreamResult.Expired(raw)
        } else {
            StreamResult.Failed(
                DownloadErrors.textFor(raw), raw, DownloadErrors.isNetworkProblem(raw),
            )
        }

    private suspend fun downloadDirect(stream: StreamRef, dest: File): StreamResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(stream.url)
                // Byte counts must match Content-Length; compressed transfer
                // would make the progress numbers lie.
                .header("Accept-Encoding", "identity")
                .build()
            val call = graph.http.newCall(request)
            activeCall = call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val raw = "HTTP ${response.code} ${response.message}"
                        return@withContext if (DownloadErrors.isExpiredLink(raw)) {
                            StreamResult.Expired(raw)
                        } else {
                            StreamResult.Failed(
                                "The server refused the file ($raw).",
                                raw,
                                network = response.code >= 500,
                            )
                        }
                    }
                    val body = response.body ?: return@withContext StreamResult.Failed(
                        "The server replied without a file.", null, network = true,
                    )
                    val total = body.contentLength().takeIf { it > 0 } ?: (stream.sizeBytes ?: 0L)
                    val startedAt = SystemClock.elapsedRealtime()
                    var received = 0L
                    body.byteStream().use { input ->
                        dest.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                received += read
                                val elapsedMs =
                                    (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                                latest.set(
                                    DownloadProgress(
                                        downloadedBytes = received,
                                        totalBytes = total,
                                        speedBytesPerSecond = received * 1000.0 / elapsedMs,
                                    )
                                )
                            }
                        }
                    }
                    StreamResult.Ok(dest)
                }
            } catch (exc: IOException) {
                if (!currentCoroutineContext().isActive) {
                    StreamResult.Cancelled
                } else {
                    val raw = exc.message
                    StreamResult.Failed(
                        DownloadErrors.textFor(raw), raw, network = true,
                    )
                }
            } finally {
                activeCall = null
            }
        }

    // ------------------------------------------------------------ assemble

    private sealed interface AssembleOutcome {
        data class Ok(val file: File) : AssembleOutcome
        data object Cancelled : AssembleOutcome
        data class Failed(val message: String, val raw: String?) : AssembleOutcome
    }

    private suspend fun assemble(
        option: QualityOption,
        files: List<File>,
        scratch: File,
        prefix: String,
    ): AssembleOutcome {
        if (files.isEmpty()) {
            return AssembleOutcome.Failed("The download produced no files.", null)
        }
        val single = files.singleOrNull()
        if (single != null && sameContainer(single, option.container)) {
            return AssembleOutcome.Ok(single)
        }

        val output = File(scratch, "${prefix}_merged.${option.container}")
        val args = mutableListOf("-y")
        files.forEach { args += listOf("-i", it.absolutePath) }
        args += listOf("-c", "copy")
        if (option.container.equals("mp4", ignoreCase = true)) {
            args += listOf("-movflags", "+faststart")
        }
        args += output.absolutePath

        val session = withContext(Dispatchers.IO) {
            FFmpegKit.executeWithArguments(args.toTypedArray())
        }
        val code = session.returnCode
        return when {
            ReturnCode.isSuccess(code) -> AssembleOutcome.Ok(output)
            ReturnCode.isCancel(code) -> AssembleOutcome.Cancelled
            else -> {
                val codeText = code?.value?.toString() ?: "unknown"
                val tail = session.output?.trim()?.takeLast(FFMPEG_TAIL_CHARS).orEmpty()
                AssembleOutcome.Failed(
                    "The media was downloaded but couldn't be assembled (merge failed).",
                    "ffmpeg exited with code $codeText. $tail",
                )
            }
        }
    }

    private fun sameContainer(file: File, container: String): Boolean {
        val ext = file.extension.lowercase()
        return ext == container.lowercase() ||
            (container.equals("mp4", ignoreCase = true) && ext in setOf("m4v", "mov"))
    }

    // ------------------------------------------------------------ verify

    private data class VerifyProblem(val message: String, val raw: String?)

    /**
     * The last line of defense against a lie: what arrived must exist, be
     * non-empty, carry the magic bytes of the container the user picked, and
     * (when a size was reported) be at least 95% of it.
     */
    private fun verify(file: File, option: QualityOption, entry: HistoryEntry): VerifyProblem? {
        if (!file.isFile) {
            return VerifyProblem("The download finished but the file is missing.", file.absolutePath)
        }
        if (file.length() <= 0L) {
            return VerifyProblem("The download finished but the file is empty.", file.absolutePath)
        }
        if (!MediaSniffer.matches(file, option.container)) {
            return VerifyProblem(
                "What arrived wasn't ${option.container.uppercase()} media — " +
                    "the link probably served a web page.",
                "magic bytes do not match container ${option.container}",
            )
        }
        val expected = option.sizeBytes ?: entry.sizeBytes
        if (expected != null && expected > 0L && file.length() * 100 < expected * SHORT_FILE_PERCENT) {
            return VerifyProblem(
                "The download stopped early (" +
                    "${FormatSize.format(file.length()).orEmpty()} of " +
                    "${FormatSize.format(expected).orEmpty()}).",
                "short file: ${file.length()} bytes is below $SHORT_FILE_PERCENT% of $expected",
            )
        }
        return null
    }

    // ------------------------------------------------------------ outcomes

    private sealed interface PipelineOutcome {
        data class Completed(
            val uri: String,
            val name: String,
            val mime: String,
            val path: String,
            val size: Long,
        ) : PipelineOutcome

        data object Cancelled : PipelineOutcome

        data class Failed(
            val message: String,
            val raw: String?,
            val network: Boolean,
        ) : PipelineOutcome
    }

    private suspend fun finishFailed(entry: HistoryEntry, failure: PipelineOutcome.Failed): Result {
        val canRetry = failure.network && runAttemptCount < MAX_NETWORK_ATTEMPTS
        val now = System.currentTimeMillis()
        if (canRetry) {
            // The row says QUEUED again; WorkManager brings the work back
            // after a 30 s exponential backoff.
            graph.history.setStatus(entry.id, DownloadStatus.QUEUED, null, failure.raw, now)
            runCatching {
                NotificationManagerCompat.from(applicationContext).notify(
                    DownloadNotifications.idFor(entry.id),
                    DownloadNotifications.failed(
                        applicationContext, entry.id, entry.title,
                        "The connection dropped — retrying…",
                    ),
                )
            }
            return Result.retry()
        }
        graph.history.setStatus(entry.id, DownloadStatus.FAILED, failure.message, failure.raw, now)
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(
                DownloadNotifications.idFor(entry.id),
                DownloadNotifications.failed(
                    applicationContext, entry.id, entry.title, failure.message,
                ),
            )
        }
        return Result.failure()
    }

    /** Cancelled by the engine hook — unless a stronger final state is already stored. */
    private suspend fun markCancelled(id: String) {
        withContext(NonCancellable) {
            val current = graph.history.getById(id) ?: return@withContext
            if (current.status != DownloadStatus.QUEUED && current.status != DownloadStatus.RUNNING) {
                return@withContext
            }
            graph.history.setStatus(id, DownloadStatus.CANCELLED, null, null, System.currentTimeMillis())
        }
    }

    private fun notifyCompleted(entry: HistoryEntry, done: PipelineOutcome.Completed) {
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(
                DownloadNotifications.idFor(entry.id),
                DownloadNotifications.completed(
                    applicationContext, entry.id, entry.title,
                    "Saved to your media library as ${done.name}",
                    done.uri, done.mime,
                ),
            )
        }
    }

    // ------------------------------------------------------------ progress

    private suspend fun withProgressPump(
        entry: HistoryEntry,
        body: suspend () -> PipelineOutcome,
    ): PipelineOutcome = coroutineScope {
        val pump = launch {
            while (isActive) {
                pushProgress(entry)
                delay(PROGRESS_PUMP_MS)
            }
        }
        try {
            body()
        } finally {
            pump.cancel()
            stage.set(null)
        }
    }

    private suspend fun pushProgress(entry: HistoryEntry) {
        val stageText = stage.get() ?: return
        val progress = latest.get()
        setProgress(
            workDataOf(
                KEY_STAGE to stageText,
                KEY_BYTES to progress.downloadedBytes,
                KEY_TOTAL to progress.totalBytes,
                KEY_SPEED to progress.speedBytesPerSecond.toLong(),
            )
        )
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(
                DownloadNotifications.idFor(entry.id),
                DownloadNotifications.progress(
                    applicationContext, entry.id, entry.title, stageText, progress,
                ),
            )
        }
    }

    private fun foregroundInfo(entry: HistoryEntry): ForegroundInfo =
        ForegroundInfo(
            DownloadNotifications.idFor(entry.id),
            DownloadNotifications.progress(
                applicationContext, entry.id, entry.title,
                "Starting…", DownloadProgress(0L, 0L, 0.0),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    // ------------------------------------------------------------ helpers

    private fun optionFrom(entry: HistoryEntry): QualityOption = QualityOption(
        id = entry.id,
        label = entry.optionLabel,
        kind = entry.kind,
        container = entry.container,
        streams = entry.signature,
        needsMerge = entry.signature.size > 1,
        sizeBytes = entry.sizeBytes,
    )

    private suspend fun refreshOption(entry: HistoryEntry): QualityOption? {
        val outcome = graph.repository.analyze(entry.sourceUrl)
        val info = (outcome as? ResolveOutcome.Success)?.info ?: return null
        return OptionMatcher.match(entry.signature, info.options)
    }

    private fun cleanupTemps(scratch: File, prefix: String) {
        scratch.listFiles { file -> file.name.startsWith(prefix) }
            ?.forEach { runCatching { it.delete() } }
    }

    /** Called only on real cancellation — never on a normal finish. */
    private fun cancelActiveWork() {
        activeHook?.requestCancel()
        activeCall?.cancel()
        FFmpegKit.cancel()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_STAGE = "stage"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"

        private const val DIRECT_FORMAT_ID = "direct"
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_PUMP_MS = 500L

        /** Two retries after the first attempt, then the failure is final. */
        private const val MAX_NETWORK_ATTEMPTS = 2
        private const val FFMPEG_TAIL_CHARS = 400
        private const val SHORT_FILE_PERCENT = 95L
    }
}
