package com.fetchly.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.session.ResolveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultUiState(
    val media: MediaInfo? = null,
    val selectedId: String? = null,
    val enqueuing: Boolean = false,
    val queued: Boolean = false,
    val error: String? = null,
) {
    val selected: QualityOption? get() = media?.options?.firstOrNull { it.id == selectedId }
}

/**
 * Result's brain: shows what Home resolved, preselects per the user's
 * default-quality preference, and hands the chosen option to the queue.
 */
class ResultViewModel(
    private val session: ResolveSession,
    defaultQuality: Flow<DefaultQuality>,
    private val commands: DownloadCommands,
) : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState(media = session.info.value))
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val choice = defaultQuality.first()
            _state.update { it.copy(selectedId = preselect(it.media?.options.orEmpty(), choice)) }
        }
    }

    fun select(optionId: String) {
        _state.update { it.copy(selectedId = optionId) }
    }

    fun download() {
        val current = _state.value
        val media = current.media ?: return
        val option = current.selected ?: return
        if (current.enqueuing || current.queued) return
        _state.update { it.copy(enqueuing = true, error = null) }
        viewModelScope.launch {
            try {
                commands.enqueue(media, option)
                _state.update { it.copy(enqueuing = false, queued = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(enqueuing = false, error = "Couldn't start the download. Check available storage and try again.")
                }
            }
        }
    }

    /** The screen navigated to Downloads; the flag must not fire again. */
    fun consumeQueued() {
        _state.update { it.copy(queued = false) }
    }

    companion object {
        /** BEST → the top option (highest video); ASK → nothing preselected. */
        fun preselect(options: List<QualityOption>, choice: DefaultQuality): String? =
            if (choice == DefaultQuality.BEST) options.firstOrNull()?.id else null
    }
}
