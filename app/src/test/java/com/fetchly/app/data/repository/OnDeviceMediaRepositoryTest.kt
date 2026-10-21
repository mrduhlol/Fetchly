package com.fetchly.app.data.repository

import com.fetchly.app.data.direct.DirectProbe
import com.fetchly.app.data.direct.DirectResult
import com.fetchly.app.data.engine.LinkExtractor
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.QualityKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceMediaRepositoryTest {

    private class FakeProbe(private val result: DirectResult) : DirectProbe {
        var calls = 0
        override suspend fun resolve(url: String): DirectResult {
            calls++
            return result
        }
    }

    private class FakeEngine(private val json: String) : LinkExtractor {
        var calls = 0
        override suspend fun extract(url: String): String {
            calls++
            return json
        }
    }

    private val engineOk =
        """{"ok":true,"title":"Engine title","uploader":"Someone","duration":10,"thumbnail":null,""" +
            """"formats":[{"id":"18","url":"https://cdn.invalid/18","ext":"mp4","vcodec":"avc1.42001E",""" +
            """"acodec":"mp4a.40.2","height":360,"abr":null,"filesize":1000,"filesize_approx":null,""" +
            """"protocol":"https"}]}"""

    @Test
    fun `social links skip the probe and go to the engine`() = runBlocking {
        val probe = FakeProbe(DirectResult.NotDirect)
        val engine = FakeEngine(engineOk)
        val repository = OnDeviceMediaRepository(probe, engine)

        val outcome = repository.analyze("https://www.youtube.com/watch?v=test123")

        assertTrue(outcome is ResolveOutcome.Success)
        assertEquals(0, probe.calls)
        assertEquals(1, engine.calls)
    }

    @Test
    fun `direct probe hit becomes a single file option and never calls the engine`() = runBlocking {
        val probe = FakeProbe(
            DirectResult.File(
                url = "https://cdn.invalid/clip.mp4",
                mime = "video/mp4",
                sizeBytes = 1234L,
                filename = "clip.mp4",
            )
        )
        val engine = FakeEngine(engineOk)
        val repository = OnDeviceMediaRepository(probe, engine)

        val outcome = repository.analyze("https://cdn.invalid/clip.mp4") as ResolveOutcome.Success

        assertEquals(1, probe.calls)
        assertEquals(0, engine.calls)
        assertEquals(MediaType.VIDEO, outcome.info.mediaType)
        assertEquals("clip", outcome.info.title)
        val option = outcome.info.options.single()
        assertEquals(QualityKind.FILE, option.kind)
        assertEquals("mp4", option.container)
        assertEquals("https://cdn.invalid/clip.mp4", option.streams.single().url)
        assertEquals(1234L, option.sizeBytes)
    }

    @Test
    fun `probe miss falls through to the engine`() = runBlocking {
        val probe = FakeProbe(DirectResult.NotDirect)
        val engine = FakeEngine(engineOk)
        val repository = OnDeviceMediaRepository(probe, engine)

        val outcome = repository.analyze("https://example.invalid/page")

        assertTrue(outcome is ResolveOutcome.Success)
        assertEquals(1, probe.calls)
        assertEquals(1, engine.calls)
    }

    @Test
    fun `engine failure is classified with the raw message kept`() = runBlocking {
        val probe = FakeProbe(DirectResult.NotDirect)
        val engine = FakeEngine("""{"ok":false,"error":"ERROR: [youtube] x: Video unavailable"}""")
        val repository = OnDeviceMediaRepository(probe, engine)

        val outcome = repository.analyze("https://www.youtube.com/watch?v=x") as ResolveOutcome.Failed

        assertEquals("The source says this media doesn't exist or has been removed.", outcome.message)
        assertEquals("ERROR: [youtube] x: Video unavailable", outcome.raw)
    }
    @Test
    fun `media endpoint extension does not override its real container`() = runBlocking {
        val probe = FakeProbe(DirectResult.File("https://cdn.invalid/download.php", "video/mp4", 123L, "download.php"))
        val repository = OnDeviceMediaRepository(probe, FakeEngine(engineOk))
        val result = repository.analyze("https://cdn.invalid/download.php") as ResolveOutcome.Success
        assertEquals("mp4", result.info.options.single().container)
    }

    @Test
    fun `direct image offers its own URL as thumbnail`() = runBlocking {
        val url = "https://cdn.invalid/photo.jpg"
        val repository = OnDeviceMediaRepository(
            FakeProbe(DirectResult.File(url, "image/jpeg", 123L, "photo.jpg")), FakeEngine(engineOk),
        )
        val result = repository.analyze(url) as ResolveOutcome.Success
        assertEquals(MediaType.IMAGE, result.info.mediaType)
        assertEquals(url, result.info.thumbnailUrl)
    }

    @Test
    fun `unmapped media types preserve their source extension`() = runBlocking {
        val url = "https://cdn.invalid/photo.avif"
        val repository = OnDeviceMediaRepository(
            FakeProbe(DirectResult.File(url, "image/avif", 123L, "photo.avif")), FakeEngine(engineOk),
        )
        val result = repository.analyze(url) as ResolveOutcome.Success
        assertEquals("avif", result.info.options.single().container)
    }

}
