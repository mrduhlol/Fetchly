package com.fetchly.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.download.DownloadWork
import com.fetchly.app.data.download.DownloadWorks
import com.fetchly.app.data.download.DownloadStages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
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
        get() = when {
            entry.status == DownloadStatus.PAUSED -> "Paused"
            entry.status == DownloadStatus.QUEUED && work?.running != true ->
                if ((work?.attempt ?: 0) > 0) "Waiting to retry when connected…" else "Waiting…"
            else -> work?.takeUnless { it.finished }?.stage ?: "Starting…"
        }

    /** Transfer percentages never describe merging, verification, or saving. */
    val bytes: Long get() = if (entry.status == DownloadStatus.PAUSED || work?.finished != false)
        entry.progressBytes else work.bytes.takeIf { it > 0 } ?: entry.progressBytes
    val total: Long get() = work?.total?.takeIf { it > 0 } ?: entry.progressTotal
    val percent: Int?
        get() = if (total > 0 && (entry.status == DownloadStatus.PAUSED ||
            entry.status == DownloadStatus.QUEUED || DownloadStages.isTransfer(stageText))) {
            (bytes.toDouble() / total * 100).toInt().coerceIn(0, 100)
        } else null

}

data class DownloadsUiState(
    val active: List<ActiveDownload> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val loading: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && active.isEmpty() && history.isEmpty()
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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds = _busyIds.asStateFlow()

    val state: StateFlow<DownloadsUiState> =
        combine(history.observeAll(), works.observe()) { entries, running ->
            map(entries, joinWorks(running))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState(loading = true))

    fun pause(id: String) = perform(id, "pause") { commands.pause(id) }
    fun cancel(id: String) = perform(id, "stop") { commands.cancel(id) }
    fun retry(id: String) = perform(id, "retry") { commands.retry(id) }
    fun delete(id: String, deleteFile: Boolean = true) = perform(id, "delete") { commands.delete(id, deleteFile) }

    private fun perform(id: String, verb: String, action: suspend () -> Boolean) {
        if (id in _busyIds.value) return
        _busyIds.update { it + id }
        viewModelScope.launch {
            try {
                if (!action()) _messages.emit("This download changed before the action finished. Check its current status.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _messages.emit("Couldn't $verb the download. Check available storage and try again.")
            } finally {
                _busyIds.update { it - id }
            }
        }
    }

    companion object {
        /**
         * One work entry per download id: a retry (REPLACE) leaves the old
         * CANCELLED spec behind next to the new one, so a live job is always
         * preferred over a finished leftover, then a running one over a
         * merely enqueued one.
         */
        fun joinWorks(works: List<DownloadWork>): Map<String, DownloadWork> =
            works.groupBy { it.downloadId }.mapValues { (_, candidates) ->
                candidates.firstOrNull { !it.finished && it.running }
                    ?: candidates.firstOrNull { !it.finished }
                    ?: candidates.first()
            }

        /** Pure mapping so the list shape is testable without Android. */
        fun map(entries: List<HistoryEntry>, works: Map<String, DownloadWork>): DownloadsUiState {
            val active = mutableListOf<ActiveDownload>()
            val done = mutableListOf<HistoryEntry>()
            for (stored in entries) {
                val work = works[stored.id]
                val interrupted = stored.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED) &&
                    work?.finished == true && (work.failed || work.cancelled)
                val entry = if (interrupted) stored.copy(status = DownloadStatus.FAILED,
                    errorMessage = "The download was interrupted. Retry to continue.",
                    errorRaw = "Download worker interrupted") else stored
                when (entry.status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED ->
                        active += ActiveDownload(entry, works[entry.id])
                    else -> done += entry
                }
            }
            return DownloadsUiState(active = active, history = done)
        }
    }
}
