package com.fetchly.app.data.repository

import com.fetchly.app.domain.model.MediaInfo

/** Result of analyzing a link, before anything is downloaded. */
sealed interface ResolveOutcome {
    data class Success(val info: MediaInfo) : ResolveOutcome

    /** [message] is the user-facing sentence; [raw] keeps the engine's own text. */
    data class Failed(val message: String, val raw: String?) : ResolveOutcome
}

interface MediaRepository {
    /**
     * Analyzes one link. [onStage] receives user-facing progress lines as the
     * real steps begin ("Checking the link…" for the probe, "Asking yt-dlp…"
     * for extraction) — no artificial delays, just honest reporting.
     */
    suspend fun analyze(url: String, onStage: (String) -> Unit = {}): ResolveOutcome
}
