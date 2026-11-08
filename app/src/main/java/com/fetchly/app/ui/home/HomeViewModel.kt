package com.fetchly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.data.repository.ResolveErrors
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.security.UrlValidation
import com.fetchly.app.domain.session.IncomingLinks
import com.fetchly.app.domain.session.ResolveSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    internal val selectedApp: SupportedApp? = null,
    internal val mode: FetchMode? = null,
    val input: String = "",
    val detected: Platform? = null,
    /** The real stage while analyzing ("Checking the link…", "Finding the good stuff…"). */
    val status: String? = null,
    val error: String? = null,
    val errorRaw: String? = null,
    /** A clipboard URL offered as a one-tap chip — never pasted on its own. */
    val suggestion: String? = null,
    val analyzing: Boolean = false,
    val success: Boolean = false,
    val batchReady: Boolean = false,
) {
    val canFetch: Boolean get() = input.isNotBlank() && !analyzing
}

/**
 * Home's brain: link in, [ResolveSession] out. Staging is real — the status
 * line mirrors the repository's own steps, no fake delays.
 */
class HomeViewModel(
    private val resolver: ResolveUrlUseCase,
    private val session: ResolveSession,
    private val incoming: IncomingLinks,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var resolveJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            incoming.text.collect { text ->
                if (text == null) return@collect
                incoming.consume()
                // A share that carries a link is prefilled (the first usable
                // one wins); a share with only a refused link, or none at
                // all, says so instead of closing onto silence.
                if (com.fetchly.app.domain.batch.BatchLinks.parse(text).urls.size > 1) {
                    cancelFetch(); session.holdBatchInput(text)
                    _state.update { it.copy(batchReady = true) }; return@collect
                }
                val valid = UrlSecurity.extractFirstUrl(text)
                if (valid != null) {
                    cancelFetch()
                    _state.update {
                        it.copy(input = valid, detected = detect(valid), error = null, errorRaw = null, suggestion = null)
                    }
                    return@collect
                }
                val candidate = UrlSecurity.firstUrlCandidate(text)
                val reason = candidate
                    ?.let { UrlSecurity.validate(it) as? UrlValidation.Invalid }
                    ?.reason
                _state.update { it.copy(error = reason ?: "That share didn't contain a link.", errorRaw = null) }
            }
        }
    }

    internal fun selectApp(app: SupportedApp) {
        if (state.value.analyzing) return
        _state.update { it.copy(selectedApp = app, mode = app.modes.first(), error = null, errorRaw = null) }
    }

    internal fun selectMode(mode: FetchMode) {
        if (!state.value.analyzing && mode in state.value.selectedApp?.modes.orEmpty())
            _state.update { it.copy(mode = mode, error = null, errorRaw = null) }
    }

    fun onInputChange(value: String) {
        val input = if (com.fetchly.app.domain.batch.BatchLinks.parse(value).urls.size > 1) value else if (value.any { it.isWhitespace() }) {
            UrlSecurity.extractFirstUrl(value) ?: value
        } else value
        _state.update { it.copy(input = input, detected = detect(input), error = null, errorRaw = null) }
    }

    /** Clipboard peek: offered as a chip the user taps, never auto-filled. */
    fun offerClipboard(text: String?) {
        val url = if (com.fetchly.app.domain.batch.BatchLinks.parse(text.orEmpty()).urls.size > 1) text!!.take(128 * 1024 + 1) else UrlSecurity.extractFirstUrl(text) ?: return
        _state.update {
            if (it.input.isBlank() && it.suggestion == null) it.copy(suggestion = url) else it
        }
    }

    fun acceptSuggestion() {
        _state.update {
            val url = it.suggestion ?: return@update it
            it.copy(input = url, detected = detect(url), suggestion = null, error = null, errorRaw = null)
        }
    }

    fun dismissSuggestion() {
        _state.update { it.copy(suggestion = null) }
    }

    fun fetch() {
        val requested = _state.value
        if (requested.analyzing) return
        if (com.fetchly.app.domain.batch.BatchLinks.parse(requested.input).urls.size > 1) { openBatch(); return }

        // Same local check the use case runs; done here too so the status
        // line can stay truthful ("Checking the link…" is really happening).
        val validated = when (val check = UrlSecurity.validate(requested.input)) {
            is UrlValidation.Valid -> check.url
            is UrlValidation.Invalid -> {
                _state.update { it.copy(error = check.reason, errorRaw = null) }
                return
            }
        }

        if (requested.selectedApp != null && PlatformDetector.detect(validated) != requested.selectedApp.platform) {
            _state.update { it.copy(error = "Paste a ${requested.selectedApp.label} link or select a different app.") }
            return
        }
        session.clear()
        _state.update { it.copy(analyzing = true, error = null, errorRaw = null, status = "Checking the link…") }
        val requestGeneration = ++generation
        resolveJob = viewModelScope.launch {
            val outcome = try {
                resolver(validated) { stage ->
                    if (generation == requestGeneration) _state.update { it.copy(status = stage) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ResolveOutcome.Failed(ResolveErrors.messageFor(error.message), error.message)
            }
            if (requestGeneration != generation) return@launch
            when (outcome) {
                is ResolveOutcome.Success -> {
                    val kind = requested.mode?.kind
                    if (kind != null && outcome.info.options.none { it.kind == kind }) {
                        _state.update { it.copy(analyzing = false, status = null, error = "No ${kind.name.lowercase()} is available for this link. Try another link or media option.") }
                        return@launch
                    }
                    session.hold(outcome.info, kind)
                    _state.update {
                        it.copy(analyzing = false, status = null, success = true)
                    }
                }
                is ResolveOutcome.Failed -> {
                    _state.update {
                        it.copy(analyzing = false, status = null, error = outcome.message, errorRaw = outcome.raw)
                    }
                }
            }
        }
    }

    /** Discard stale results even if the blocking extractor is still unwinding. */
    fun cancelFetch() {
        generation++
        resolveJob?.cancel()
        resolveJob = null
        _state.update { it.copy(analyzing = false, status = null, success = false) }
    }

    /** The screen has navigated; the flag must not fire again on the way back. */
    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }

    fun openBatch() {
        val requested = state.value
        val app = requested.selectedApp
        if (app != null && com.fetchly.app.domain.batch.BatchLinks.parse(requested.input).urls.any { PlatformDetector.detect(it) != app.platform }) {
            _state.update { it.copy(error = "Use ${app.label} links for this batch, or select a different app.") }
            return
        }
        cancelFetch()
        session.holdBatchInput(state.value.input, state.value.mode?.kind)
        _state.update { it.copy(batchReady = true) }
    }
    fun consumeBatchReady() { _state.update { it.copy(batchReady = false) } }

    private fun detect(url: String): Platform? =
        if (url.isBlank()) null else PlatformDetector.detect(url).takeIf { it != Platform.OTHER }
}
