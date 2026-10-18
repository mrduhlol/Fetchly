package com.fetchly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.repository.ResolveOutcome
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
    val input: String = "",
    val detected: Platform? = null,
    /** The real stage while analyzing ("Checking the link…", "Asking yt-dlp…"). */
    val status: String? = null,
    val error: String? = null,
    /** A clipboard URL offered as a one-tap chip — never pasted on its own. */
    val suggestion: String? = null,
    val analyzing: Boolean = false,
    val success: Boolean = false,
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

    init {
        viewModelScope.launch {
            incoming.text.collect { text ->
                if (text == null) return@collect
                incoming.consume()
                // A share that carries a link is prefilled (the first usable
                // one wins); a share with only a refused link, or none at
                // all, says so instead of closing onto silence.
                val valid = UrlSecurity.extractFirstUrl(text)
                if (valid != null) {
                    _state.update {
                        it.copy(input = valid, detected = detect(valid), error = null, suggestion = null)
                    }
                    return@collect
                }
                val candidate = UrlSecurity.firstUrlCandidate(text)
                val reason = candidate
                    ?.let { UrlSecurity.validate(it) as? UrlValidation.Invalid }
                    ?.reason
                _state.update { it.copy(error = reason ?: "That share didn't contain a link.") }
            }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value, detected = detect(value), error = null) }
    }

    /** Clipboard peek: offered as a chip the user taps, never auto-filled. */
    fun offerClipboard(text: String?) {
        val url = UrlSecurity.extractFirstUrl(text) ?: return
        _state.update {
            if (it.input.isBlank() && it.suggestion == null) it.copy(suggestion = url) else it
        }
    }

    fun acceptSuggestion() {
        _state.update {
            val url = it.suggestion ?: return@update it
            it.copy(input = url, detected = detect(url), suggestion = null, error = null)
        }
    }

    fun dismissSuggestion() {
        _state.update { it.copy(suggestion = null) }
    }

    fun fetch() {
        val requested = _state.value
        if (requested.analyzing) return

        // Same local check the use case runs; done here too so the status
        // line can stay truthful ("Checking the link…" is really happening).
        val validated = when (val check = UrlSecurity.validate(requested.input)) {
            is UrlValidation.Valid -> check.url
            is UrlValidation.Invalid -> {
                _state.update { it.copy(error = check.reason) }
                return
            }
        }

        session.clear()
        _state.update { it.copy(analyzing = true, error = null, status = "Checking the link…") }
        viewModelScope.launch {
            val outcome = resolver(validated) { stage ->
                _state.update { it.copy(status = stage) }
            }
            when (outcome) {
                is ResolveOutcome.Success -> {
                    session.hold(outcome.info)
                    _state.update {
                        it.copy(analyzing = false, status = null, success = true)
                    }
                }
                is ResolveOutcome.Failed -> {
                    _state.update {
                        it.copy(analyzing = false, status = null, error = outcome.message)
                    }
                }
            }
        }
    }

    /** The screen has navigated; the flag must not fire again on the way back. */
    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }

    private fun detect(url: String): Platform? =
        if (url.isBlank()) null else PlatformDetector.detect(url).takeIf { it != Platform.OTHER }
}
