package com.fetchly.app.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.util.MediaSniffer
import com.fetchly.app.domain.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Persistent background download. Streams to a temp file, verifies it, then
 * publishes to MediaStore — completion is only reported after verification.
 * Progress (bytes + speed) is throttled to ~2 updates/sec.
 */
class DownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Untitled media"
        val quality = inputData.getString(KEY_QUALITY) ?: "Best"
        val container = inputData.getString(KEY_CONTAINER) ?: "mp4"
        val mediaType = runCatching { MediaType.valueOf(inputData.getString(KEY_TYPE) ?: "VIDEO") }
            .getOrDefault(MediaType.VIDEO)
        val historyId = inputData.getLong(KEY_HISTORY_ID, -1)

        if (historyId >= 0) {
            AppGraph.history.updateWorkRequestId(historyId, id.toString())
            AppGraph.history.updateStatus(historyId, DownloadStatus.DOWNLOADING, null)
        }
        setForeground(makeForeground(title, historyId, 0))
        if (historyId >= 0) {
            DownloadNotifications.showProgress(context, historyId, title, 0, "Starting…")
        }

        val fileName = MediaStoreSaver.buildFileName(title, quality, container)
        val tmp = MediaStoreSaver.tempFile(context, fileName)
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "Fetchly/1.2").build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful || res.body == null) {
                    fail(historyId, title, "The server refused the download.")
                    return if (runAttemptCount < 2) Result.retry() else Result.failure()
                }
                val total = res.body!!.contentLength()
                if (total > UrlSecurity.MAX_DOWNLOAD_BYTES) {
                    fail(historyId, title, "This file is too large.")
                    return Result.failure()
                }
                var done = 0L
                var lastTick = 0L
                var lastDone = 0L
                var lastTimeNs = System.nanoTime()
                var speedBps = 0.0
                withContext(Dispatchers.IO) {
                    tmp.outputStream().use { out ->
                        res.body!!.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (isStopped) {
                                    tmp.delete()
                                    return@withContext
                                }
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastTick > 500) {
                                    val nowNs = System.nanoTime()
                                    val dt = (nowNs - lastTimeNs) / 1e9
                                    if (dt > 0) speedBps = (done - lastDone) / dt
                                    lastDone = done
                                    lastTimeNs = nowNs
                                    lastTick = now
                                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                    setForeground(makeForeground(title, historyId, pct))
                                    if (historyId >= 0) {
                                        DownloadNotifications.showProgress(
                                            context, historyId, title, pct,
                                            if (pct >= 0) "${MimeTypes.displaySize(done)} of ${MimeTypes.displaySize(total)}"
                                            else MimeTypes.displaySize(done),
                                        )
                                        setProgress(
                                            Data.Builder()
                                                .putLong(KEY_HID, historyId)
                                                .putLong(KEY_DONE, done)
                                                .putLong(KEY_TOTAL, total)
                                                .putDouble(KEY_SPEED, speedBps)
                                                .build()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (isStopped) {
                    tmp.delete()
                    if (historyId >= 0) {
                        AppGraph.history.updateStatus(historyId, DownloadStatus.CANCELLED, null)
                        DownloadNotifications.dismiss(context, historyId)
                    }
                    return Result.failure()
                }
                // Verify before claiming completion: real size AND real media bytes.
                // HTML/JSON error pages must never be saved as media.
                val header = ByteArray(16)
                val headerLen = withContext(Dispatchers.IO) {
                    runCatching {
                        tmp.inputStream().use { it.read(header) }
                    }.getOrDefault(-1)
                }
                if (!tmp.exists() || tmp.length() <= 0 ||
                    (total > 0 && tmp.length() < total) ||
                    headerLen < 4 || !MediaSniffer.looksLikeMedia(header.copyOf(maxOf(headerLen, 4)))
                ) {
                    tmp.delete()
                    fail(historyId, title, "The server returned an error instead of media.")
                    return if (runAttemptCount < 2) Result.retry() else Result.failure()
                }
                val entry = MediaStoreSaver.createEntry(context, mediaType, container, fileName)
                    ?: run {
                        tmp.delete()
                        fail(historyId, title, "Couldn't save to your device.")
                        return Result.failure()
                    }
                context.contentResolver.openOutputStream(entry)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
                MediaStoreSaver.markComplete(context, entry)
                if (historyId >= 0) {
                    AppGraph.history.updateStatus(historyId, DownloadStatus.COMPLETED, entry.toString())
                    DownloadNotifications.showComplete(
                        context, historyId, title, fileName, entry.toString(), container
                    )
                }
                Result.success(Data.Builder().putString("uri", entry.toString()).build())
            }
        } catch (e: UnknownHostException) {
            tmp.delete()
            if (historyId >= 0) {
                DownloadNotifications.showProgress(
                    context, historyId, title, -1, "Connection lost — will retry when possible."
                )
            }
            failHistory(historyId)
            if (runAttemptCount < 2) Result.retry() else {
                fail(historyId, title, "Check your internet connection and try again.")
                Result.failure()
            }
        } catch (e: IOException) {
            tmp.delete()
            val noSpace = e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                e.message?.contains("No space", ignoreCase = true) == true
            failHistory(historyId)
            if (noSpace) {
                fail(historyId, title, "Not enough storage on this device.")
                Result.failure()
            } else if (runAttemptCount < 2) {
                Result.retry()
            } else {
                fail(historyId, title, "The connection dropped.")
                Result.failure()
            }
        } catch (e: Exception) {
            tmp.delete()
            failHistory(historyId)
            fail(historyId, title, "Something went wrong.")
            Result.failure()
        }
    }

    private suspend fun fail(historyId: Long, title: String, reason: String) {
        failHistory(historyId)
        if (historyId >= 0) DownloadNotifications.showFailed(context, historyId, title, reason)
    }

    private suspend fun failHistory(id: Long) {
        if (id >= 0) {
            runCatching {
                val current = AppGraph.history.getById(id)
                AppGraph.history.updateStatus(id, DownloadStatus.FAILED, current?.localUri)
            }
        }
    }

    private fun makeForeground(title: String, historyId: Long, pct: Int): ForegroundInfo {
        DownloadNotifications.ensureChannel(context)
        val n = NotificationCompat.Builder(context, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (pct >= 0) "$pct% downloading" else "Downloading…")
            .setProgress(100, pct.coerceIn(0, 100), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val nid = if (historyId >= 0) DownloadNotifications.idFor(historyId) else NOTIF_FALLBACK
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(nid, n)
        }
    }

    companion object {
        const val NOTIF_FALLBACK = 2001
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_QUALITY = "quality"
        const val KEY_CONTAINER = "container"
        const val KEY_TYPE = "type"
        const val KEY_HISTORY_ID = "history_id"
        const val KEY_HID = "hid"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
    }
}
