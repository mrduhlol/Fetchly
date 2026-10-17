package com.fetchly.app.data.db

import com.fetchly.app.domain.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturesTest {

    private val streams = listOf(
        StreamRef(
            formatId = "137",
            url = "https://cdn.example.invalid/137",
            ext = "mp4",
            vcodec = "avc1.640028",
            acodec = "none",
            height = 1080,
            abr = null,
            sizeBytes = 80_911_999L,
            protocol = "https",
        ),
        StreamRef(
            formatId = "140",
            url = "https://cdn.example.invalid/140",
            ext = "m4a",
            vcodec = "none",
            acodec = "mp4a.40.2",
            height = null,
            abr = 129,
            sizeBytes = null,
            protocol = null,
        ),
    )

    @Test
    fun `round trip keeps every field`() {
        val decoded = Signatures.decode(Signatures.encodeSignature(streams), Signatures.encodeUrls(streams))
        assertEquals(streams, decoded)
    }

    @Test
    fun `urls are rejoined by format id, not by position`() {
        val urls = Signatures.encodeUrls(streams.reversed())
        val decoded = Signatures.decode(Signatures.encodeSignature(streams), urls)
        assertEquals(streams, decoded)
    }

    @Test
    fun `missing urls decode as empty strings`() {
        val decoded = Signatures.decode(Signatures.encodeSignature(streams), "{}")
        assertEquals(streams.map { it.copy(url = "") }, decoded)
    }

    @Test
    fun `blank signature decodes to empty`() {
        assertTrue(Signatures.decode(null, null).isEmpty())
        assertTrue(Signatures.decode("", "{}").isEmpty())
        assertTrue(Signatures.decode("   ", null).isEmpty())
    }

    @Test
    fun `malformed json degrades to empty instead of crashing`() {
        assertTrue(Signatures.decode("not json at all", "{}").isEmpty())
    }

    @Test
    fun `malformed urls fall back to empty urls, signature survives`() {
        val decoded = Signatures.decode(Signatures.encodeSignature(streams), "{not json")
        assertEquals(streams.map { it.copy(url = "") }, decoded)
    }
}
