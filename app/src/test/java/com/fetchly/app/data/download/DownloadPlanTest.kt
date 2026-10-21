package com.fetchly.app.data.download

import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPlanTest {
    @Test fun `audio selected from a video belongs in the music library`() {
        assertEquals(MediaType.AUDIO, DownloadPlan.mediaType(QualityKind.AUDIO, MediaType.VIDEO))
        assertEquals(MediaType.IMAGE, DownloadPlan.mediaType(QualityKind.FILE, MediaType.IMAGE))
    }

    @Test fun `selected audio wins over audio embedded in the video`() {
        val option = option(QualityKind.VIDEO, listOf(stream("h264", "aac"), stream("none", "opus")))
        assertEquals(listOf("-map", "0:v:0", "-map", "1:a:0"), DownloadPlan.streamMaps(option))
    }

    @Test fun `stream order does not change the video and audio mapping`() {
        val option = option(QualityKind.VIDEO, listOf(stream("none", "aac"), stream("h264", "none")))
        assertEquals(listOf("-map", "1:v:0", "-map", "0:a:0"), DownloadPlan.streamMaps(option))
    }

    @Test fun `audio output never includes a video track`() {
        assertEquals(listOf("-map", "0:a:0", "-vn"), DownloadPlan.streamMaps(
            option(QualityKind.AUDIO, listOf(stream("h264", "aac"))),
        ))
    }

    private fun option(kind: QualityKind, streams: List<StreamRef>) =
        QualityOption("id", "quality", kind, "mp4", streams, streams.size > 1, null)

    private fun stream(video: String, audio: String) =
        StreamRef("id", "https://example.com/media", "mp4", video, audio, null, null, null)
}
