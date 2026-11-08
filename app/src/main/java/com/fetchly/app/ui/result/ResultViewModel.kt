package com.fetchly.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityKind
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
    val activeKind: QualityKind? = media?.options?.firstOrNull { it.kind == QualityKind.VIDEO && !it.silentVideo }?.kind
        ?: media?.options?.firstOrNull()?.kind,
    val outputTitle: String = media?.title.orEmpty(),
    val queuedMessage: String? = null,
    val enqueuing: Boolean = false,
    val queued: Boolean = false,
    val error: String? = null,
) {
    val visibleOptions: List<QualityOption> get() = media?.options.orEmpty().filter { it.kind == activeKind }
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

    private val _state = MutableStateFlow(ResultUiState(media = session.info.value).let { initial ->
        session.preferredKind?.takeIf { kind -> initial.media?.options.orEmpty().any { it.kind == kind } }
            ?.let { initial.copy(activeKind = it) } ?: initial
    })
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private var qualityPreference = DefaultQuality.ASK

    init {
        viewModelScope.launch {
            val choice = defaultQuality.first()
            qualityPreference = choice
            _state.update { it.copy(selectedId = preselect(it.visibleOptions, choice)) }
        }
    }

    fun select(optionId: String) {
        _state.update {
            if (it.enqueuing || it.visibleOptions.none { option -> option.id == optionId }) it
            else it.copy(selectedId = optionId)
        }
    }

    fun selectKind(kind: QualityKind) {
        _state.update { current ->
            val options = current.media?.options.orEmpty().filter { it.kind == kind }
            if (current.enqueuing || options.isEmpty()) current
            else current.copy(
                activeKind = kind,
                selectedId = preselect(options, qualityPreference),
            )
        }
    }

    fun rename(value: String) { _state.update { if (it.enqueuing) it else it.copy(outputTitle = value.take(240)) } }

    fun download() {
        val current = _state.value
        val media = current.media ?: return
        val option = current.selected ?: return
        if (current.enqueuing || current.queued) return
        _state.update { it.copy(enqueuing = true, error = null) }
        viewModelScope.launch {
            try {
                val receipt = commands.enqueueWithReceipt(media.copy(title = com.fetchly.app.domain.util.Filenames.sanitize(current.outputTitle.ifBlank { media.title })), option)
                _state.update { it.copy(enqueuing = false, queued = true, queuedMessage = receipt.disposition.message) }
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
            if (choice == DefaultQuality.BEST) options.filter { !it.silentVideo }
                .sortedWith(compareByDescending<QualityOption> { option -> option.streams.maxOfOrNull { it.height ?: 0 } ?: 0 }
                    .thenByDescending { option -> option.streams.maxOfOrNull { it.languagePreference ?: 0 } ?: 0 }
                    .thenByDescending { option -> option.streams.maxOfOrNull { it.abr ?: 0 } ?: 0 })
                .firstOrNull()?.id else null
    }
}
