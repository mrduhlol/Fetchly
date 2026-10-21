package com.fetchly.app.data.download

import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption

/** Choices shared by queueing and publishing must describe the selected output. */
internal object DownloadPlan {
    fun mediaType(kind: QualityKind, source: MediaType): MediaType = when (kind) {
        QualityKind.AUDIO -> MediaType.AUDIO
        QualityKind.VIDEO -> MediaType.VIDEO
        QualityKind.FILE -> source
    }

    /** Explicit maps prevent FFmpeg from substituting audio from the video input. */
    fun streamMaps(option: QualityOption): List<String> {
        if (option.streams.isEmpty()) return emptyList()
        if (option.kind == QualityKind.AUDIO) return listOf("-map", "0:a:0", "-vn")
        if (option.streams.size == 1) return listOf("-map", "0")
        val video = option.streams.indexOfFirst { it.vcodec != "none" }
        // Prefer the separately selected audio stream over a video's embedded track.
        val audio = option.streams.indexOfFirst { it.vcodec == "none" && it.acodec != "none" }
            .takeIf { it >= 0 }
            ?: option.streams.indexOfFirst { it.acodec != "none" }
        return buildList {
            if (video >= 0) addAll(listOf("-map", "$video:v:0"))
            if (audio >= 0) addAll(listOf("-map", "$audio:a:0"))
        }
    }
}
