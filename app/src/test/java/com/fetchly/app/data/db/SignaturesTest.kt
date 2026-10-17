package com.fetchly.app.data.db

import com.fetchly.app.domain.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturesTest {

    private val video = StreamRef(
        formatId = "137",
        url = "https://cdn.example.invalid/137?exp=1",
        ext = "mp4",
        vcodec = "avc1.640028",
        acodec = "none",
        height = 1080,
        abr = null,
        sizeBytes = 80_911_999,
        protocol = "https",
    )

    private val audio = StreamRef(
        formatId = "140",
        url = "https://cdn.example.invalid/140?exp=1",
        ext = "m4a",
        vcodec = "none",
        acodec = "mp4a.40.2",
        height = null,
        abr = 129,
        sizeBytes = null,
        protocol = null,
    )

    @Test
    fun `round trip keeps every field including urls and nulls`() {
        val streams = listOf(video, audio)

        val decoded = Signatures.decode(
            Signatures.encodeSignature(streams),
            Signatures.encodeUrls(streams),
        )

        assertEquals(streams, decoded)
    }

    @Test
    fun `a stream without a cached url decodes with an empty url`() {
        val decoded = Signatures.decode(
            Signatures.encodeSignature(listOf(video)),
            Signatures.encodeUrls(emptyList()),
        )

        assertEquals(1, decoded.size)
        assertEquals("", decoded.single().url)
        assertEquals(video.copy(url = ""), decoded.single())
    }

    @Test
    fun `signature json does not leak urls`() {
        val signatureJson = Signatures.encodeSignature(listOf(video))

        assertTrue("signature should not contain a url: $signatureJson", !signatureJson.contains("https://"))
    }

    @Test
    fun `malformed or missing json decodes to an empty list`() {
        assertTrue(Signatures.decode(null, null).isEmpty())
        assertTrue(Signatures.decode("not json", null).isEmpty())
        assertTrue(Signatures.decode(null, "{bad").isEmpty())
        assertTrue(Signatures.decode("[{}]", null).let { it.size == 1 })
    }
}
