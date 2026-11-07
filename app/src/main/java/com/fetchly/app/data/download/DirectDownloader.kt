package com.fetchly.app.data.download

import com.fetchly.app.data.engine.DownloadProgress
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Validated HTTP resume, bounded per-request retries, and four ranges for large files. */
internal class DirectDownloader(
    private val http: OkHttpClient,
    private val maxRetries: Int = 2,
    private val retryDelayMillis: Long = 500L,
    private val onRetry: (attempt: Int, waitMillis: Long) -> Unit = { _, _ -> },
) {
    init {
        require(maxRetries in 0..5 && retryDelayMillis >= 0)
    }

    suspend fun download(url: String, dest: File, progress: (DownloadProgress) -> Unit): File =
        withContext(Dispatchers.IO) {
            withDestinationLock(dest) {
                try {
                    transfer(url, dest, progress, allowRanges = true)
                } catch (_: RangeRejected) {
                    // All siblings have stopped before discarding their checkpoints.
                    currentCoroutineContext().ensureActive()
                    HttpDownloadState.clear(dest)
                    transfer(url, dest, progress, allowRanges = false)
                }
                dest
            }
        }

    private suspend fun transfer(
        url: String, dest: File, progress: (DownloadProgress) -> Unit, allowRanges: Boolean,
    ) {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        var pending: HttpDownloadState? = null
        retryRequest {
            pending = null
            execute(request) { response ->
                if (response.code != 200) throw httpFailure(response)
                val body = response.body ?: throw IOException("The server replied without a file.")
                val total = body.contentLength()
                val etag = response.header("ETag")?.takeIf { it.startsWith('"') && it.endsWith('"') }
                if (allowRanges && total > 0 && etag != null &&
                    response.header("Accept-Ranges").equals("bytes", ignoreCase = true) && identityEncoding(response)
                ) {
                    val count = if (total >= MIN_PARALLEL_BYTES) CONNECTIONS else 1
                    val state = HttpDownloadState.open(dest, url, response.request.url.toString(), total, etag, count)
                    // Small new files use this response directly, avoiding a second request.
                    if (count == 1 && state.downloaded() == 0L) {
                        val received = AtomicLong()
                        copyRange(response, dest, state, 0, received, System.nanoTime(), 0, progress)
                    } else pending = state
                } else {
                    // No strong validator: retries must start from zero, never append.
                    HttpDownloadState.clear(dest)
                    val started = System.nanoTime()
                    var received = 0L
                    body.byteStream().use { input ->
                        val output = storageIo { dest.outputStream() }
                        output.use {
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                storageIo { output.write(buffer, 0, count) }
                                received += count
                                progress(snapshot(received, total.coerceAtLeast(0), started, 0))
                            }
                        }
                    }
                    if (total >= 0 && received != total) throw IOException("Incomplete HTTP body: $received of $total bytes")
                }
            }
        }
        // Each segment handles its own retries. Do not wrap the entire group in
        // retryRequest: that would multiply retry budgets and repeat good work.
        pending?.let { downloadRanges(it, dest, progress) }
    }

    private suspend fun downloadRanges(state: HttpDownloadState, dest: File, progress: (DownloadProgress) -> Unit) {
        val initial = state.downloaded()
        val received = AtomicLong(initial)
        val started = System.nanoTime()
        progress(snapshot(initial, state.total, started, initial))
        coroutineScope {
            (0 until state.count).map { index ->
                async(Dispatchers.IO) {
                    retryRequest {
                        val offset = state.offset(index)
                        if (offset == state.length(index)) return@retryRequest
                        val start = state.start(index) + offset
                        val end = state.end(index)
                        val request = Request.Builder().url(state.resolvedUrl)
                            .header("Accept-Encoding", "identity")
                            .header("Range", "bytes=$start-$end")
                            .header("If-Range", state.etag).build()
                        execute(request) { response ->
                            if (response.code != 206 && response.code != 200 && response.code != 416) {
                                throw httpFailure(response)
                            }
                            if (response.code != 206 ||
                                response.header("Content-Range") != "bytes $start-$end/${state.total}" ||
                                response.header("ETag") != state.etag || !identityEncoding(response) ||
                                response.request.url.toString() != state.resolvedUrl
                            ) throw RangeRejected()
                            val size = response.body?.contentLength() ?: throw RangeRejected()
                            if (size >= 0 && size != end - start + 1) throw RangeRejected()
                            copyRange(response, dest, state, index, received, started, initial, progress)
                        }
                    }
                }
            }.awaitAll()
        }
        progress(snapshot(state.total, state.total, started, initial))
    }

    private suspend fun copyRange(
        response: Response, dest: File, state: HttpDownloadState, index: Int,
        received: AtomicLong, started: Long, initial: Long, progress: (DownloadProgress) -> Unit,
    ) {
        val body = response.body ?: throw IOException("The server replied without a file.")
        var written = state.offset(index)
        var lastCheckpoint = written
        var checkpointAt = System.nanoTime()
        body.byteStream().use { input ->
            storageIo { RandomAccessFile(dest, "rw") }.use { output ->
                storageIo { output.seek(state.start(index) + written) }
                try {
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (written < state.length(index)) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), state.length(index) - written).toInt())
                        if (count < 0) throw IOException("Incomplete HTTP range")
                        storageIo { output.write(buffer, 0, count) }
                        written += count
                        progress(snapshot(received.addAndGet(count.toLong()), state.total, started, initial))
                        if (written - lastCheckpoint >= CHECKPOINT_BYTES || System.nanoTime() - checkpointAt >= CHECKPOINT_NANOS) {
                            state.checkpoint(index, written, output)
                            lastCheckpoint = written
                            checkpointAt = System.nanoTime()
                        }
                    }
                    if (input.read() != -1) throw RangeRejected()
                } finally {
                    // Includes network failure and cancellation; record only bytes
                    // actually written, never the preallocated file's length.
                    if (written > lastCheckpoint) state.checkpoint(index, written, output)
                }
            }
        }
    }

    private suspend fun <T> execute(request: Request, block: suspend (Response) -> T): T {
        val call = http.newCall(request)
        return withDownloadCancellation(call::cancel) {
            try {
                call.execute().use { block(it) }
            } catch (exc: IOException) {
                currentCoroutineContext().ensureActive()
                throw exc
            }
        }
    }

    private suspend fun <T> retryRequest(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return block()
            } catch (exc: IOException) {
                currentCoroutineContext().ensureActive()
                if (!isRetryable(exc) || attempt >= maxRetries) throw exc
                val backoff = (retryDelayMillis.coerceAtMost(MAX_RETRY_DELAY_MS) * (1L shl attempt)).coerceAtMost(MAX_RETRY_DELAY_MS)
                val wait = maxOf(backoff, (exc as? HttpFailure)?.retryAfterMillis ?: 0L)
                attempt++
                onRetry(attempt, wait)
                delay(wait)
            }
        }
    }

    private fun isRetryable(exc: IOException): Boolean = when (exc) {
        is RangeRejected, is DownloadStorageException, is SSLHandshakeException, is SSLPeerUnverifiedException -> false
        is HttpFailure -> exc.code in RETRYABLE_HTTP
        else -> true
    }

    private fun identityEncoding(response: Response) = response.header("Content-Encoding").let { it == null || it.equals("identity", true) }
    private fun httpFailure(response: Response): HttpFailure {
        val header = response.header("Retry-After")
        val seconds = header?.toLongOrNull()
        val wait = if (seconds != null) seconds.coerceIn(0L, MAX_RETRY_DELAY_MS / 1000) * 1000 else {
            header?.let {
                runCatching {
                    (ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
                        System.currentTimeMillis()).coerceIn(0L, MAX_RETRY_DELAY_MS)
                }.getOrNull()
            }
        }
        return HttpFailure(response.code, response.message, wait)
    }

    private fun snapshot(bytes: Long, total: Long, started: Long, initial: Long) = DownloadProgress(
        bytes, total, (bytes - initial).coerceAtLeast(0) * 1_000_000_000.0 / (System.nanoTime() - started).coerceAtLeast(1),
    )

    class HttpFailure(val code: Int, message: String, val retryAfterMillis: Long? = null) : IOException("HTTP $code $message")
    private class RangeRejected : IOException("The server rejected byte ranges")

    // WorkManager REPLACE may start a retry while the cancelled worker is
    // still flushing its last bytes. Serialize attempts for one destination.
    private suspend fun <T> withDestinationLock(dest: File, block: suspend () -> T): T {
        val key = dest.absoluteFile.normalize().path
        val lease = synchronized(destinationLocks) {
            destinationLocks.getOrPut(key) { DestinationLock() }.also { it.users++ }
        }
        try {
            return lease.mutex.withLock { block() }
        } finally {
            synchronized(destinationLocks) {
                if (--lease.users == 0) destinationLocks.remove(key)
            }
        }
    }

    private class DestinationLock(val mutex: Mutex = Mutex(), var users: Int = 0)

    companion object {
        private val destinationLocks = mutableMapOf<String, DestinationLock>()
        private const val CONNECTIONS = 4
        private const val MIN_PARALLEL_BYTES = 8L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val CHECKPOINT_BYTES = 4L * 1024 * 1024
        private const val CHECKPOINT_NANOS = 2_000_000_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private val RETRYABLE_HTTP = setOf(408, 429, 500, 502, 503, 504)
    }
}
