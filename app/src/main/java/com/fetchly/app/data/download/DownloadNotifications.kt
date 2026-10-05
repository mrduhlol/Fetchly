package com.fetchly.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fetchly.app.domain.util.MimeTypes

/** All download notifications go through here. One id per download. */
object DownloadNotifications {

    const val CHANNEL_ID = "fetchly_downloads"
    private const val NOTIF_BASE = 2100

    fun idFor(historyId: Long): Int = NOTIF_BASE + (historyId % 900).toInt()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun showProgress(
        context: Context,
        historyId: Long,
        title: String,
        pct: Int,
        text: String,
    ) {
        ensureChannel(context)
        val cancel = PendingIntent.getBroadcast(
            context,
            historyId.toInt(),
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_CANCEL)
                .putExtra(DownloadActionReceiver.EXTRA_HISTORY_ID, historyId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText(if (pct >= 0) "$pct% • $text" else text)
            .setProgress(100, pct.coerceIn(0, 100), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .build()
        notify(context, historyId, n)
    }

    fun showComplete(
        context: Context,
        historyId: Long,
        title: String,
        fileName: String,
        uriString: String,
        container: String,
    ) {
        ensureChannel(context)
        val mime = MimeTypes.resolve(context, uriString, container)
        val open = PendingIntent.getActivity(
            context,
            historyId.toInt(),
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(fileName.ifBlank { title })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notify(context, historyId, n)
    }

    fun showFailed(
        context: Context,
        historyId: Long,
        title: String,
        reason: String,
    ) {
        ensureChannel(context)
        val retry = PendingIntent.getBroadcast(
            context,
            historyId.toInt(),
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_RETRY)
                .putExtra(DownloadActionReceiver.EXTRA_HISTORY_ID, historyId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Download failed")
            .setContentText("$title — $reason")
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_popup_sync, "Retry", retry)
            .build()
        notify(context, historyId, n)
    }

    fun dismiss(context: Context, historyId: Long) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(idFor(historyId))
    }

    private fun notify(context: Context, historyId: Long, n: android.app.Notification) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(idFor(historyId), n)
    }
}
