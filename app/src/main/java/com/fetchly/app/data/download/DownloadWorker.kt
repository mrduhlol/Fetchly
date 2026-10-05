package com.fetchly.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fetchly.app.R
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.security.UrlSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Persistent background download with progress + completion notifications.
 * Streams directly to a temp file, then publishes to MediaStore — never
 * holds media in memory. Survives app backgrounding via WorkManager.
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

        setForeground(makeForeground(title, 0))
        notify(title, "Download started", 0, true)

        val fileName = MediaStoreSaver.buildFileName(title, quality, container)
        val tmp = MediaStoreSaver.tempFile(context, fileName)
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "Fetchly/1.0").build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful || res.body == null) {
                    notify(title, "Download failed", 0, false)
                    failHistory(historyId)
                    return Result.retry()
                }
                val total = res.body!!.contentLength()
                if (total > UrlSecurity.MAX_DOWNLOAD_BYTES) {
                    notify(title, "File too large", 0, false)
                    failHistory(historyId)
                    return Result.failure()
                }
                var done = 0L
                var lastTick = 0L
                val startNs = System.nanoTime()
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
                                    lastTick = now
                                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                    setForeground(makeForeground(title, pct))
                                    val speed = done * 1e9 / maxOf(1L, System.nanoTime() - startNs)
                                    notify(
                                        title,
                                        if (pct >= 0) "$pct% • ${formatMB(done)}" else formatMB(done),
                                        pct, true,
                                    )
                                    setProgress(Data.Builder().putLong("done", done).putLong("total", total).build())
                                }
                            }
                        }
                    }
                }
                if (isStopped) {
                    tmp.delete()
                    return Result.failure()
                }
                // Publish to MediaStore
                val entry = MediaStoreSaver.createEntry(context, mediaType, container, fileName)
                    ?: return Result.failure()
                context.contentResolver.openOutputStream(entry)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
                MediaStoreSaver.markComplete(context, entry)
                completeHistory(historyId, entry.toString())
                notify(title, "Download complete", 100, false)
                Result.success(Data.Builder().putString("uri", entry.toString()).build())
            }
        } catch (e: Exception) {
            tmp.delete()
            notify(title, "Download failed", 0, false)
            failHistory(historyId)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun channel(): String {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel("fetchly_downloads", "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return "fetchly_downloads"
    }

    private fun makeForeground(title: String, pct: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(context, channel())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (pct >= 0) "$pct% downloading" else "Downloading…")
            .setProgress(100, pct.coerceIn(0, 100), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, n)
        }
    }

    private fun notify(title: String, text: String, pct: Int, ongoing: Boolean) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, channel())
            .setSmallIcon(
                if (ongoing) android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_download_done
            )
            .setContentTitle("Fetchly • $title")
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(ongoing)
            .apply { if (ongoing && pct >= 0) setProgress(100, pct, false) }
            .build()
        mgr.notify(NOTIF_ID, n)
    }

    private fun failHistory(id: Long) {
        if (id >= 0) AppGraph.history.updateStatusSync(id, DownloadStatus.FAILED, null)
    }

    private fun completeHistory(id: Long, uri: String) {
        if (id >= 0) AppGraph.history.updateStatusSync(id, DownloadStatus.COMPLETED, uri)
    }

    private fun formatMB(bytes: Long): String = "%.1f MB".format(bytes / 1024.0 / 1024.0)

    companion object {
        const val NOTIF_ID = 2001
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_QUALITY = "quality"
        const val KEY_CONTAINER = "container"
        const val KEY_TYPE = "type"
        const val KEY_HISTORY_ID = "history_id"
    }
}

// Tiny bridge so the Worker can update Room without Hilt.
fun com.fetchly.app.domain.repository.HistoryRepository.updateStatusSync(
    id: Long, status: DownloadStatus, uri: String?,
) {
    kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.Dispatchers.IO.let {
            kotlinx.coroutines.withContext(it) { updateStatus(id, status, uri) }
        }
    }
}
