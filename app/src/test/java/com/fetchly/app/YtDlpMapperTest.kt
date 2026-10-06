package com.fetchly.app

import com.fetchly.app.data.engine.YtDlpMapper
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMapperTest {

    private val sample = """
        {"ok": true, "title": "Amazing Video", "uploader": "creator",
         "duration": 272, "thumbnail": "https://img/x.jpg",
         "formats": [
           {"id": "22", "quality": "720p", "height": 720, "ext": "mp4",
            "filesize": 45000000, "url": "https://dl/22",
            "vcodec": "avc1", "acodec": "mp4a", "abr": 0},
           {"id": "18", "quality": "360p", "height": 360, "ext": "mp4",
            "filesize": 25000000, "url": "https://dl/18",
            "vcodec": "avc1", "acodec": "mp4a", "abr": 0},
           {"id": "140", "quality": "", "height": 0, "ext": "m4a",
            "filesize": 5200000, "url": "https://dl/140",
            "vcodec": "none", "acodec": "mp4a", "abr": 128},
           {"id": "sb", "quality": "", "height": 0, "ext": "mhtml",
            "filesize": 1, "url": "https://dl/sb",
            "vcodec": "none", "acodec": "none", "abr": 0},
           {"id": "nou", "quality": "", "height": 1080, "ext": "mp4",
            "filesize": 80000000, "url": "",
            "vcodec": "avc1", "acodec": "none", "abr": 0}
         ]}
    """.trimIndent()

    @Test fun mapsVideoAndAudio() {
        val out = YtDlpMapper.fromJson("https://youtu.be/abc", Platform.YOUTUBE, sample)
        assertNull(out.error)
        val info = assertNotNull(out.info)
        assertEquals("Amazing Video", info.title)
        assertEquals("creator", info.author)
        assertEquals(272L, info.durationSecs)
        assertEquals("https://img/x.jpg", info.thumbnailUrl)
        assertEquals(MediaType.VIDEO, info.mediaType)
        assertEquals(2, info.videoFormats.size)
        assertEquals("720p", info.videoFormats[0].quality)
        assertEquals("360p", info.videoFormats[1].quality)
        assertEquals(1, info.audioFormats.size)
        assertEquals("128 kbps", info.audioFormats[0].bitrateLabel)
        // Storyboard (mhtml) and URL-less formats are dropped, never offered.
        assertTrue(info.formats.none { it.container == "mhtml" || it.downloadUrl.isBlank() })
    }

    @Test fun reportsExtractorFailure() {
        val out = YtDlpMapper.fromJson(
            "https://youtu.be/abc", Platform.YOUTUBE,
            """{"ok": false, "error": "EXTRACT_FAILED: Private video"}""",
        )
        assertNull(out.info)
        assertEquals("EXTRACT_FAILED: Private video", out.error)
    }

    @Test fun reportsEmptyFormats() {
        val out = YtDlpMapper.fromJson(
            "https://youtu.be/abc", Platform.YOUTUBE,
            """{"ok": true, "title": "x", "formats": []}""",
        )
        assertNull(out.info)
        assertEquals("no_formats", out.error)
    }

    private fun assertNotNull(value: com.fetchly.app.domain.model.MediaInfo?): com.fetchly.app.domain.model.MediaInfo {
        assertTrue(value != null)
        return value!!
    }
}
