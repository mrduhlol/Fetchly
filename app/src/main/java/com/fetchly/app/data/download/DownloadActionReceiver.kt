package com.fetchly.app.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fetchly.app.FetchlyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the two buttons on download notifications. Not exported: only the
 * app's own PendingIntents target it, so no other app can cancel or retry
 * anything.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val action = intent.action ?: return
        val queue = (context.applicationContext as? FetchlyApp)?.graph?.queue ?: return

        // onReceive must return fast; the queue work runs on a background scope.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CANCEL -> queue.cancel(downloadId)
                    ACTION_RETRY -> queue.retry(downloadId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.fetchly.app.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY = "com.fetchly.app.action.RETRY_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
