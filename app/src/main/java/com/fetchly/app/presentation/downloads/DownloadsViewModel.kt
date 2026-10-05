package com.fetchly.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.AppGraph
import com.fetchly.app.data.download.DlProgress
import com.fetchly.app.data.download.RetryOutcome
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.util.MimeTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadRowState(
    val entry: HistoryEntry,
    val progress: DlProgress?,
) {
    val isActive: Boolean
        get() = entry.status == DownloadStatus.QUEUED ||
            entry.status == DownloadStatus.DOWNLOADING ||
            entry.status == DownloadStatus.PAUSED
}

class DownloadsViewModel : ViewModel() {

    val rows: StateFlow<List<DownloadRowState>> =
        combine(AppGraph.history.observe(), AppGraph.observeProgress()) { history, progress ->
            history.map { entry ->
                DownloadRowState(entry, entry.workRequestId?.let { progress[it] })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(entry: HistoryEntry) = viewModelScope.launch {
        when (val out = AppGraph.retry(entry.id)) {
            is RetryOutcome.Started -> Unit
            is RetryOutcome.NoStorage -> _notice.value =
                "Not enough storage: need ${MimeTypes.displaySize(out.needBytes)}, " +
                    "have ${MimeTypes.displaySize(out.freeBytes)}."
            is RetryOutcome.Unusable -> _notice.value =
                "This download can't be retried. Paste the link again."
        }
    }

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    fun cancel(entry: HistoryEntry) {
        AppGraph.cancel(entry.id)
    }

    /** Remove the history entry; the file stays on the device. */
    fun removeFromHistory(entry: HistoryEntry) = viewModelScope.launch {
        AppGraph.history.delete(entry)
    }

    /** Delete the actual downloaded file (explicit user choice). Returns success. */
    suspend fun deleteFile(entry: HistoryEntry): Boolean =
        AppGraph.deleteDownloadedFile(entry)

    fun clear() = viewModelScope.launch {
        AppGraph.history.clear()
    }
}
