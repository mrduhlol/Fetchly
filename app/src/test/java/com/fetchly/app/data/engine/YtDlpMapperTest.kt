package com.fetchly.app.data.engine

import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMapperTest {

    private val sourceUrl = "https://www.youtube.com/watch?v=test123"
    private val platform = Platform.YOUTUBE

    // ------------------------------------------------------------ helpers

    private fun mapped(json: String): MediaInfo {
        val result = YtDlpMapper.map(json, sourceUrl, platform)
        assertTrue("expected Ok but was $result", result is MapResult.Ok)
        return (result as MapResult.Ok).info
    }

    private fun num(value: Any?): String = value?.toString() ?: "null"

    private fun fmt(
        id: String,
        ext: String = "mp4",
        vcodec: String = "none",
        acodec: String = "none",
        height: Int? = null,
        abr: Double? = null,
        filesize: Long? = null,
        approx: Long? = null,
    ): String =
        """{"id":"$id","url":"https://cdn.example.invalid/$id","ext":"$ext",""" +
            """"vcodec":"$vcodec","acodec":"$acodec","height":${num(height)},""" +
            """"abr":${num(abr)},"filesize":${num(filesize)},""" +
            """"filesize_approx":${num(approx)},"protocol":"https"}"""

    private fun ok(vararg formats: String): String =
        """{"ok":true,"title":"Test video","uploader":"Someone","duration":213,""" +
            """"thumbnail":"https://i.example.invalid/t.jpg","formats":[${formats.joinToString(",")}]}"""

    private fun fail(error: String): String = """{"ok":false,"error":"$error"}"""

    // ------------------------------------------------------------ tests

    @Test
    fun `split streams pair with the best compatible audio`() {
        val info = mapped(
            ok(
                fmt("137", vcodec = "avc1.640028", height = 1080, filesize = 80_911_999),
                fmt("248", ext = "webm", vcodec = "vp9", height = 1080, filesize = 30_846_580),
                fmt("140", ext = "m4a", acodec = "mp4a.40.2", abr = 129.5, filesize = 3_449_447),
                fmt("251", ext = "webm", acodec = "opus", abr = 128.9, filesize = 3_433_755),
            )
        )
        assertEquals(MediaType.VIDEO, info.mediaType)
        assertEquals(listOf("v1080", "audio"), info.options.map { it.id })

        val video = info.options[0]
        assertEquals("1080p", video.label)
        assertEquals(QualityKind.VIDEO, video.kind)
        assertEquals("mp4", video.container)
        assertTrue(video.needsMerge)
        assertEquals(listOf("137", "140"), video.streams.map { it.formatId })
        assertEquals(80_911_999L + 3_449_447L, video.sizeBytes)

        val audio = info.options[1]
        assertEquals("Audio", audio.label)
        assertEquals(QualityKind.AUDIO, audio.kind)
        assertEquals("m4a", audio.container)
        assertFalse(audio.needsMerge)
        assertEquals("140", audio.streams.single().formatId)
        assertEquals(3_449_447L, audio.sizeBytes)
    }

    @Test
    fun `non avc video with opus audio becomes mkv and webm`() {
        val info = mapped(
            ok(
                fmt("248", ext = "webm", vcodec = "vp9", height = 1080, filesize = 30_000_000),
                fmt("251", ext = "webm", acodec = "opus", abr = 128.9, filesize = 3_300_000),
            )
        )
        val video = info.options.first { it.id == "v1080" }
        assertEquals("mkv", video.container)
        assertEquals(listOf("248", "251"), video.streams.map { it.formatId })
        val audio = info.options.first { it.id == "audio" }
        assertEquals("webm", audio.container)
    }

    @Test
    fun `muxed stream downloads alone with no merge`() {
        val info = mapped(
            ok(
                fmt("18", vcodec = "avc1.42001E", acodec = "mp4a.40.2", height = 360, filesize = 9_000_000),
                fmt("137", vcodec = "avc1.640028", height = 1080, filesize = 80_000_000),
                fmt("140", ext = "m4a", acodec = "mp4a.40.2", abr = 129.5, filesize = 3_400_000),
            )
        )
        val muxed = info.options.first { it.id == "v360" }
        assertEquals("360p", muxed.label)
        assertEquals(listOf("18"), muxed.streams.map { it.formatId })
        assertFalse(muxed.needsMerge)
        assertEquals("mp4", muxed.container)

        val split = info.options.first { it.id == "v1080" }
        assertTrue(split.needsMerge)
        assertEquals(2, split.streams.size)
    }

    @Test
    fun `muxed beats split at the same height`() {
        val info = mapped(
            ok(
                fmt("137", vcodec = "avc1.640028", height = 360, filesize = 900_000_000),
                fmt("18", vcodec = "avc1.42001E", acodec = "mp4a.40.2", height = 360, filesize = 9_000_000),
                fmt("140", ext = "m4a", acodec = "mp4a.40.2", abr = 129.5, filesize = 3_400_000),
            )
        )
        val option = info.options.first { it.id == "v360" }
        assertEquals("18", option.streams.single().formatId)
        assertFalse(option.needsMerge)
    }

    @Test
    fun `video without any audio gets the no audio label`() {
        val info = mapped(ok(fmt("137", vcodec = "avc1.640028", height = 1080, filesize = 80_000_000)))
        val option = info.options.single()
        assertEquals("1080p (no audio)", option.label)
        assertFalse(option.needsMerge)
        assertEquals("mp4", option.container)
        assertEquals(MediaType.VIDEO, info.mediaType)
    }

    @Test
    fun `sizes are summed only when every stream size is known`() {
        val bothKnown = mapped(
            ok(
                fmt("137", vcodec = "avc1", height = 1080, filesize = 80_000_000),
                fmt("140", ext = "m4a", acodec = "mp4a", abr = 129.0, filesize = 3_000_000),
            )
        ).options.first { it.id == "v1080" }
        assertEquals(83_000_000L, bothKnown.sizeBytes)

        val audioUnknown = mapped(
            ok(
                fmt("137", vcodec = "avc1", height = 1080, filesize = 80_000_000),
                fmt("140", ext = "m4a", acodec = "mp4a", abr = 129.0),
            )
        ).options.first { it.id == "v1080" }
        assertNull(audioUnknown.sizeBytes)

        val approxOnly = mapped(
            ok(
                fmt("137", vcodec = "avc1", height = 1080, approx = 70_000_000),
                fmt("140", ext = "m4a", acodec = "mp4a", abr = 129.0),
            )
        ).options.first { it.id == "v1080" }
        assertNull(approxOnly.sizeBytes)
    }

    @Test
    fun `single stream links degrade to one file option`() {
        val audio = mapped(ok(fmt("0", ext = "mp3", acodec = "mp3")))
        assertEquals(listOf("file"), audio.options.map { it.id })
        assertEquals("File", audio.options.single().label)
        assertEquals("mp3", audio.options.single().container)
        assertEquals(QualityKind.FILE, audio.options.single().kind)
        assertEquals(MediaType.AUDIO, audio.mediaType)

        val plain = mapped(ok(fmt("hls-1", ext = "mp4")))
        assertEquals("File", plain.options.single().label)
        assertEquals(MediaType.VIDEO, plain.mediaType)
    }

    @Test
    fun `image links become an image file option`() {
        val info = mapped(ok(fmt("0", ext = "jpg", filesize = 250_000)))
        assertEquals(MediaType.IMAGE, info.mediaType)
        assertEquals(listOf("file"), info.options.map { it.id })
        assertEquals("Image", info.options.single().label)
        assertEquals("jpg", info.options.single().container)
        assertEquals(250_000L, info.options.single().sizeBytes)
    }

    @Test
    fun `signin failure keeps the raw yt dlp message`() {
        val raw = "ERROR: [youtube] test123: Sign in to confirm you're not a bot."
        val result = YtDlpMapper.map(fail(raw), sourceUrl, platform)
        assertEquals(MapResult.Fail(raw), result)
    }

    @Test
    fun `malformed json fails cleanly`() {
        val result = YtDlpMapper.map("not json at all", sourceUrl, platform)
        assertTrue("expected Fail but was $result", result is MapResult.Fail)
    }

    @Test
    fun `storyboards alone are not usable media`() {
        val result = YtDlpMapper.map(ok(fmt("sb0", ext = "mhtml")), sourceUrl, platform)
        assertTrue("expected Fail but was $result", result is MapResult.Fail)
    }

    @Test
    fun `real youtube fixture maps to expected options`() {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream("extract_sample.json")) {
            "extract_sample.json missing from test resources"
        }.bufferedReader().use { it.readText() }
        val info = mapped(json)

        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", info.title)
        assertEquals("Rick Astley", info.uploader)
        assertEquals(213, info.durationSeconds)
        assertEquals(MediaType.VIDEO, info.mediaType)

        val videoIds = info.options.filter { it.kind == QualityKind.VIDEO }.map { it.id }
        assertEquals(
            listOf("v2160", "v1440", "v1080", "v720", "v480", "v360", "v240", "v144"),
            videoIds,
        )
        assertEquals(9, info.options.size) // 8 heights + one audio option

        val v2160 = info.options.first { it.id == "v2160" }
        assertEquals("2160p", v2160.label)
        assertTrue(v2160.needsMerge)
        assertEquals("mkv", v2160.container) // best 2160 codec here is vp9
        assertEquals("313", v2160.streams.first().formatId)
        assertEquals("140", v2160.streams.last().formatId)

        val v1080 = info.options.first { it.id == "v1080" }
        assertEquals("mp4", v1080.container)
        assertEquals("137", v1080.streams.first().formatId)
        assertEquals(80_911_999L + 3_449_447L, v1080.sizeBytes)

        val audio = info.options.first { it.id == "audio" }
        assertEquals("m4a", audio.container)
        assertEquals("140", audio.streams.single().formatId)
        assertEquals(3_449_447L, audio.sizeBytes)
    }
}
