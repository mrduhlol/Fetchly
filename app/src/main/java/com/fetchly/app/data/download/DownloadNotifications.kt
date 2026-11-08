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

    const val CHANNEL_ID = "fetchly_transfers_v2"

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
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun progress(
        context: Context,
        downloadId: String,
        title: String,
        stage: String,
        progress: DownloadProgress,
        thumbnail: android.graphics.Bitmap? = null,
    ): Notification {
        val display = DownloadStages.progressFor(stage, progress)
        val knownTotal = display.totalBytes > 0
        return builder(context, title, progressText(stage, display), largeIcon = false)
            .apply { thumbnail?.let { setLargeIcon(it) } }
            .setSmallIcon(R.drawable.ic_stat_moai_emoji)
            .setOngoing(true)
            .addAction(0, "Pause", broadcast(context, downloadId, DownloadActionReceiver.ACTION_PAUSE, salt = 3))
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(if (knownTotal) 100 else 0, percentOf(display), !knownTotal)
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
        thumbnail: android.graphics.Bitmap? = null,
    ): Notification =
        builder(context, title, text, largeIcon = false)
            .setSmallIcon(R.drawable.ic_stat_moai_emoji_done)
            .apply { thumbnail?.let { setLargeIcon(it) } }
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openFile(context, uri, mime))
            .addAction(0, "Open file", openFile(context, uri, mime))
            .build()

    fun failed(
        context: Context,
        downloadId: String,
        title: String,
        message: String,
        retryAction: Boolean = true,
    ): Notification =
        builder(context, title, message, largeIcon = false)
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
            (progress.downloadedBytes.toDouble() / progress.totalBytes * 100).toInt().coerceIn(0, 100)
        }

    // Render Android's actual emoji once, rather than allocating it for every progress update.
    private val moaiIcon: android.graphics.Bitmap by lazy {
        android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 104f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT
            }
            val baseline = 64f - (paint.ascent() + paint.descent()) / 2f
            android.graphics.Canvas(bitmap).drawText("🗿", 64f, baseline, paint)
        }
    }

    private val moaiDoneIcon: android.graphics.Bitmap by lazy {
        moaiIcon.copy(android.graphics.Bitmap.Config.ARGB_8888, true).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            canvas.drawRoundRect(27f, 41f, 57f, 61f, 5f, 5f, paint)
            canvas.drawRoundRect(64f, 41f, 94f, 61f, 5f, 5f, paint)
            paint.strokeWidth = 5f
            canvas.drawLine(25f, 43f, 98f, 43f, paint)
        }
    }

    private fun builder(context: Context, title: String, text: String, largeIcon: Boolean = false): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply {
                if (largeIcon) {
                    setLargeIcon(moaiIcon)
                }
            }

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
