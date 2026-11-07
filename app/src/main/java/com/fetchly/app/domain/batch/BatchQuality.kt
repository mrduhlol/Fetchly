package com.fetchly.app.domain.batch

import com.fetchly.app.domain.model.*

enum class BatchQuality(val label: String) {
    BEST("Best video with sound"), P1080("Video up to 1080p"), P720("Video up to 720p"), AUDIO("Audio only");

    fun choose(media: MediaInfo): QualityOption? {
        val options = media.options
        if (options.all { it.kind == QualityKind.FILE }) return options.firstOrNull()
        val candidates = if (this == AUDIO) options.filter { it.kind == QualityKind.AUDIO }
            else options.filter { it.kind == QualityKind.VIDEO && !it.silentVideo }
        val cap = when (this) { P1080 -> 1080; P720 -> 720; else -> Int.MAX_VALUE }
        return candidates.filter { option ->
            (option.streams.mapNotNull { it.height }.maxOrNull() ?: 0) <= cap
        }.sortedWith(compareByDescending<QualityOption> { it.streams.mapNotNull { s -> s.height }.maxOrNull() ?: 0 }
            .thenByDescending { it.streams.mapNotNull { s -> s.languagePreference }.maxOrNull() ?: 0 }
            .thenByDescending { it.streams.mapNotNull { s -> s.abr }.maxOrNull() ?: 0 }).firstOrNull()
    }
}
