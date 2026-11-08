package com.fetchly.app.domain.session

import com.fetchly.app.domain.model.MediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The analysis the user just made. Home writes it on success; Result reads it
 * and guards against arriving without one (a fresh process, a back stack
 * restore). Cleared when the user starts a different link.
 */
class ResolveSession {

    private val _batchInput = MutableStateFlow<String?>(null)
    val batchInput = _batchInput.asStateFlow()
    var batchKind: com.fetchly.app.domain.model.QualityKind? = null
        private set
    fun holdBatchInput(text: String, kind: com.fetchly.app.domain.model.QualityKind? = null) {
        batchKind = kind
        _batchInput.value = text.take(128 * 1024 + 1)
    }
    fun consumeBatchInput() { _batchInput.value = null }

    private val _info = MutableStateFlow<MediaInfo?>(null)
    val info: StateFlow<MediaInfo?> = _info.asStateFlow()

    var preferredKind: com.fetchly.app.domain.model.QualityKind? = null
        private set

    fun hold(info: MediaInfo, kind: com.fetchly.app.domain.model.QualityKind? = null) {
        preferredKind = kind
        _info.value = info
    }

    fun clear() {
        preferredKind = null
        _info.value = null
    }
}

/**
 * Text handed to Fetchly from outside: a share sheet's payload or a VIEW
 * link. Home collects it, routes one link or a batch for review, then [consume]s it
 * — so the same link can be shared twice and still land.
 */
class IncomingLinks {

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    fun share(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        _text.value = trimmed.take(MAX_LENGTH)
    }

    fun consume() {
        _text.value = null
    }

    private companion object {
        const val MAX_LENGTH = 128 * 1024 + 1
    }
}
