package com.fetchly.app.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles notification actions: cancel a running download, retry a failed one. */
class DownloadActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        if (historyId < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CANCEL -> AppGraph.cancel(historyId)
                    ACTION_RETRY -> when (val out = AppGraph.retry(historyId)) {
                        is RetryOutcome.Started -> Unit
                        is RetryOutcome.NoStorage -> {
                            val title = AppGraph.history.getById(historyId)?.title ?: "Download"
                            DownloadNotifications.showFailed(
                                context, historyId, title,
                                "Not enough storage to retry.",
                            )
                        }
                        is RetryOutcome.Unusable -> {
                            val title = AppGraph.history.getById(historyId)?.title ?: "Download"
                            DownloadNotifications.showFailed(
                                context, historyId, title,
                                "Can't be retried. Paste the link again.",
                            )
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.fetchly.app.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY = "com.fetchly.app.action.RETRY_DOWNLOAD"
        const val EXTRA_HISTORY_ID = "history_id"
    }
}
