package com.fetchly.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.fetchly.app.MainActivity
import com.fetchly.app.R
import com.fetchly.app.data.engine.DownloadProgress
import com.fetchly.app.domain.util.FormatSize

/**
 * Everything the user sees about a download: a progress notification while it
 * runs (with a cancel action), a tappable "saved" notification when it lands
 * in the media library, and a failure notification that offers one retry.
 *
 * One id per download ([idFor]) so each notification replaces its own
 * predecessor instead of stacking. Terminal notifications use
 * [terminalIdFor] — a different id — because WorkManager cancels the
 * foreground notification id itself when the work ends, which would wipe a
 * terminal notification that reused it.
 */
object DownloadNotifications {

    const val CHANNEL_ID = "fetchly_downloads"

    /** Progress + foreground id; WorkManager owns this one's lifetime. */
    fun idFor(downloadId: String): Int = downloadId.hashCode()

    /** Completed/failed/retrying id, kept clear of WorkManager's cancel. */
    fun terminalIdFor(downloadId: String): Int = ("done:$downloadId").hashCode()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notifications_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    fun progress(
        context: Context,
        downloadId: String,
        title: String,
        stage: String,
        progress: DownloadProgress,
    ): Notification {
        val knownTotal = progress.totalBytes > 0
        return builder(context, title, progressText(stage, progress))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(if (knownTotal) 100 else 0, percentOf(progress), !knownTotal)
            .addAction(
                0,
                context.getString(R.string.notification_action_cancel),
                broadcast(context, downloadId, DownloadActionReceiver.ACTION_CANCEL, salt = 1),
            )
            .build()
    }

    fun completed(
        context: Context,
        downloadId: String,
        title: String,
        text: String,
        uri: String?,
        mime: String?,
    ): Notification =
        builder(context, title, text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openFile(context, uri, mime))
            .build()

    fun failed(
        context: Context,
        downloadId: String,
        title: String,
        message: String,
        retryAction: Boolean = true,
    ): Notification =
        builder(context, title, message)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .apply {
                if (retryAction) {
                    addAction(
                        0,
                        context.getString(R.string.notification_action_retry),
                        broadcast(context, downloadId, DownloadActionReceiver.ACTION_RETRY, salt = 2),
                    )
                }
            }
            .build()

    /** "Downloading video… 12.4 MB of 84.0 MB · 2.1 MB/s" — parts that are unknown are omitted. */
    private fun progressText(stage: String, progress: DownloadProgress): String = buildString {
        append(stage)
        if (progress.downloadedBytes > 0) {
            append(' ')
            append(size(progress.downloadedBytes))
            if (progress.totalBytes > 0) append(" of ${size(progress.totalBytes)}")
        }
        if (progress.speedBytesPerSecond > 0) {
            append(" · ${size(progress.speedBytesPerSecond.toLong())}/s")
        }
    }

    private fun percentOf(progress: DownloadProgress): Int =
        if (progress.totalBytes <= 0) {
            0
        } else {
            ((progress.downloadedBytes * 100) / progress.totalBytes).toInt().coerceIn(0, 100)
        }

    private fun builder(context: Context, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))

    private fun broadcast(
        context: Context,
        downloadId: String,
        action: String,
        salt: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            downloadId.hashCode() * 31 + salt,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(action)
                .putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Opens the saved file in whatever gallery/player handles it. */
    private fun openFile(context: Context, uri: String?, mime: String?): PendingIntent =
        uri?.let {
            PendingIntent.getActivity(
                context,
                it.hashCode(),
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(it), mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } ?: openApp(context)

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun size(bytes: Long): String = FormatSize.format(bytes).orEmpty()
}
