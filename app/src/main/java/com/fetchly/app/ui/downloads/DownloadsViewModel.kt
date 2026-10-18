package com.fetchly.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.download.DownloadWork
import com.fetchly.app.data.download.DownloadWorks
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One not-yet-finished download joined with its live WorkManager progress. */
data class ActiveDownload(
    val entry: HistoryEntry,
    val work: DownloadWork?,
) {
    /** Honest fallback when WorkManager hasn't reported a stage yet. */
    val stageText: String
        get() = work?.stage
            ?: if (entry.status == DownloadStatus.QUEUED) "Waiting…" else "Working…"

    /** null → the size is unknown, so the bar stays indeterminate. */
    val percent: Int?
        get() = work?.takeIf { it.total > 0L }
            ?.let { ((it.bytes * 100) / it.total).toInt().coerceIn(0, 100) }
}

data class DownloadsUiState(
    val active: List<ActiveDownload> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && history.isEmpty()
}

/**
 * Downloads' brain: history rows from the database, joined with live work
 * progress, split into "active" and "history". Actions go straight to the
 * queue — this screen never touches WorkManager itself.
 */
class DownloadsViewModel(
    history: HistoryRepository,
    private val commands: DownloadCommands,
    works: DownloadWorks,
) : ViewModel() {

    val state: StateFlow<DownloadsUiState> =
        combine(history.observeAll(), works.observe()) { entries, running ->
            map(entries, running.associateBy { it.downloadId })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun cancel(id: String) {
        viewModelScope.launch { commands.cancel(id) }
    }

    fun retry(id: String) {
        viewModelScope.launch { commands.retry(id) }
    }

    fun delete(id: String, deleteFile: Boolean = true) {
        viewModelScope.launch { commands.delete(id, deleteFile) }
    }

    companion object {
        /** Pure mapping so the list shape is testable without Android. */
        fun map(entries: List<HistoryEntry>, works: Map<String, DownloadWork>): DownloadsUiState {
            val active = mutableListOf<ActiveDownload>()
            val done = mutableListOf<HistoryEntry>()
            for (entry in entries) {
                when (entry.status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING ->
                        active += ActiveDownload(entry, works[entry.id])
                    else -> done += entry
                }
            }
            return DownloadsUiState(active = active, history = done)
        }
    }
}
