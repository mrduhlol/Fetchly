package com.fetchly.app.domain.resolve

import com.fetchly.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VideoContainersTest {
    @Test fun `MKV-only source gains a real MP4 remux choice without losing source streams`() {
        val stream = StreamRef("v", "https://example.com/v", "webm", "vp9", "opus", 1080, null, 123L)
        val source = QualityOption("v1080", "1080p", QualityKind.VIDEO, "mkv", listOf(stream), false, 123L)
        val media = MediaInfo("https://example.com", Platform.YOUTUBE, "title", null, 10, null, MediaType.VIDEO, listOf(source))
        val options = VideoContainers.apply(media).options
        assertEquals(listOf("mp4", "mkv"), options.map { it.container })
        assertTrue(options.first().needsMerge)
        assertEquals(source.streams, options.first().streams)
        assertEquals(123L, options.first().sizeBytes)
        assertEquals(2, options.map { it.id }.distinct().size)
        assertEquals(options, VideoContainers.apply(VideoContainers.apply(media)).options)
    }
}
