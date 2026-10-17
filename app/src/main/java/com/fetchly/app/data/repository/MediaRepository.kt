package com.fetchly.app.data.repository

import com.fetchly.app.domain.model.MediaInfo

/** Result of analyzing a link, before anything is downloaded. */
sealed interface ResolveOutcome {
    data class Success(val info: MediaInfo) : ResolveOutcome

    /** [message] is the user-facing sentence; [raw] keeps the engine's own text. */
    data class Failed(val message: String, val raw: String?) : ResolveOutcome
}

interface MediaRepository {
    suspend fun analyze(url: String): ResolveOutcome
}
