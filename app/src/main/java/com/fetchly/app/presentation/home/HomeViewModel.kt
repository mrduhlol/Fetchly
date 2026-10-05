package com.fetchly.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.AppGraph
import com.fetchly.app.domain.model.FetchState
import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.repository.AnalyzeResult
import com.fetchly.app.domain.usecase.AnalyzeUrlUseCase
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
)

class HomeViewModel : ViewModel() {

    private val analyze = AnalyzeUrlUseCase(AppGraph.mediaRepository())

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
            _ui.value = _ui.value.copy(state = FetchState.ANALYZING, error = null, info = null)
            when (val r = analyze(target)) {
                is AnalyzeResult.Success -> {
                    val best = r.info.videoFormats.firstOrNull() ?: r.info.formats.firstOrNull()
                    _ui.value = _ui.value.copy(
                        state = FetchState.READY,
                        info = r.info,
                        selectedFormatId = best?.id,
                    )
                }
                is AnalyzeResult.Failure -> {
                    _ui.value = _ui.value.copy(state = FetchState.ERROR, error = r.message)
                }
            }
        }
    }

    fun selectFormat(id: String) {
        _ui.value = _ui.value.copy(selectedFormatId = id)
    }

    fun selectedFormat(): MediaFormat? =
        _ui.value.info?.formats?.firstOrNull { it.id == _ui.value.selectedFormatId }

    fun downloadSelected() {
        val info = _ui.value.info ?: return
        val format = selectedFormat() ?: return
        _ui.value = _ui.value.copy(state = FetchState.DOWNLOADING)
        AppGraph.enqueueDownload(info, format)
        _ui.value = _ui.value.copy(state = FetchState.COMPLETED)
    }

    fun resetToIdle() {
        _ui.value = _ui.value.copy(state = FetchState.IDLE, error = null)
    }
}
