package com.fetchly.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.AppGraph
import com.fetchly.app.data.download.EnqueueOutcome
import com.fetchly.app.domain.model.FetchState
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.repository.ResolveResult
import com.fetchly.app.domain.usecase.ResolveUrlUseCase
import com.fetchly.app.domain.util.MimeTypes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val url: String = "",
    val state: FetchState = FetchState.IDLE,
    val error: String? = null,
    val info: MediaInfo? = null,
    val selectedFormatId: String? = null,
    /** Set when the requested media+format is already downloaded. */
    val duplicateOf: HistoryEntry? = null,
    val storageError: String? = null,
    // Debug info (dev diagnostics only).
    val lastAnalysisMs: Long? = null,
    val lastResolveStatus: String? = null,
)

class HomeViewModel : ViewModel() {

    private val analyze = ResolveUrlUseCase(AppGraph.mediaRepository())

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var job: Job? = null

    fun onUrlChange(value: String) {
        _ui.value = _ui.value.copy(url = value, error = null)
    }

    fun prefill(url: String) {
        if (url.isNotBlank()) _ui.value = _ui.value.copy(url = url.trim(), error = null)
    }

    fun fetch() {
        val target = _ui.value.url
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                state = FetchState.ANALYZING, error = null, info = null,
                duplicateOf = null, storageError = null,
            )
            val started = System.currentTimeMillis()
            when (val r = analyze(target)) {
                is ResolveResult.Success -> {
                    val best = r.info.videoFormats.firstOrNull() ?: r.info.formats.firstOrNull()
                    _ui.value = _ui.value.copy(
                        state = FetchState.READY,
                        info = r.info,
                        selectedFormatId = best?.id,
                        lastAnalysisMs = System.currentTimeMillis() - started,
                        lastResolveStatus = "OK • ${r.info.formats.size} formats",
                    )
                }
                is ResolveResult.Failure -> {
                    _ui.value = _ui.value.copy(
                        state = FetchState.ERROR,
                        error = r.message,
                        lastAnalysisMs = System.currentTimeMillis() - started,
                        lastResolveStatus = "Failed",
                    )
                }
            }
        }
    }

    fun selectFormat(id: String) {
        _ui.value = _ui.value.copy(selectedFormatId = id)
    }

    fun selectedFormat(): MediaFormat? =
        _ui.value.info?.formats?.firstOrNull { it.id == _ui.value.selectedFormatId }

    /** First tap: checks duplicates + storage, then either dialogs or enqueues. */
    fun downloadSelected() {
        val info = _ui.value.info ?: return
        val format = selectedFormat() ?: run {
            _ui.value = _ui.value.copy(error = "Pick a quality first.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(state = FetchState.DOWNLOADING)
            when (val out = AppGraph.enqueueDownload(info, format)) {
                is EnqueueOutcome.Started ->
                    _ui.value = _ui.value.copy(state = FetchState.COMPLETED)
                is EnqueueOutcome.Duplicate ->
                    _ui.value = _ui.value.copy(state = FetchState.READY, duplicateOf = out.existing)
                is EnqueueOutcome.NoStorage ->
                    _ui.value = _ui.value.copy(
                        state = FetchState.READY,
                        storageError = "Not enough storage. You need approximately " +
                            "${MimeTypes.displaySize(out.needBytes)} but only " +
                            "${MimeTypes.displaySize(out.freeBytes)} is available.",
                    )
            }
        }
    }

    /** User confirmed "Download again" on a duplicate. */
    fun confirmDuplicateDownload() {
        val info = _ui.value.info ?: return
        val format = selectedFormat() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(duplicateOf = null, state = FetchState.DOWNLOADING)
            // Bypass the duplicate guard — the user explicitly asked again.
            val entryId = AppGraph.history.add(
                HistoryEntry(
                    title = info.title,
                    sourceUrl = info.sourceUrl,
                    platform = info.platform,
                    mediaType = info.mediaType,
                    quality = format.quality,
                    container = format.container,
                    fileName = com.fetchly.app.data.download.MediaStoreSaver
                        .buildFileName(info.title, format.quality, format.container),
                    localUri = null,
                    thumbnailUrl = info.thumbnailUrl,
                    status = com.fetchly.app.domain.model.DownloadStatus.QUEUED,
                    formatId = format.id,
                    downloadUrl = format.downloadUrl,
                )
            )
            AppGraph.retry(entryId)
            _ui.value = _ui.value.copy(state = FetchState.COMPLETED)
        }
    }

    fun dismissDuplicate() {
        _ui.value = _ui.value.copy(duplicateOf = null)
    }

    fun dismissStorageError() {
        _ui.value = _ui.value.copy(storageError = null)
    }

    /** Consume the one-shot download-started event after navigating. */
    fun consumeCompleted() {
        if (_ui.value.state == FetchState.COMPLETED) {
            _ui.value = _ui.value.copy(state = FetchState.READY)
        }
    }
}
