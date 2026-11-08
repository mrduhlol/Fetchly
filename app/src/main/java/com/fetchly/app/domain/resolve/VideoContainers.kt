package com.fetchly.app.domain.resolve

import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityKind

/** Both choices refer to actual source streams; the worker remuxes the selected output. */
object VideoContainers {
    fun apply(media: MediaInfo): MediaInfo {
        val video = media.options.filter { it.kind == QualityKind.VIDEO }
        if (video.isEmpty()) return media
        val outputs = video.groupBy { it.label }.values.flatMap { choices ->
            listOf("mp4", "mkv").map { container ->
                choices.firstOrNull { it.container == container }
                    ?: choices.first().let { source -> source.copy(
                        id = "${source.id}-as-$container", container = container, needsMerge = true,
                    ) }
            }
        }
        return media.copy(options = outputs + media.options.filter { it.kind != QualityKind.VIDEO })
    }
}
