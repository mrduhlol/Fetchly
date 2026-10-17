package com.fetchly.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSecurityTest {

    private fun valid(raw: String?): String =
        (UrlSecurity.validate(raw) as UrlValidation.Valid).url

    private fun invalidReason(raw: String?): String =
        (UrlSecurity.validate(raw) as UrlValidation.Invalid).reason

    @Test
    fun `accepts normal links and trims surrounding whitespace`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", valid("  https://youtu.be/dQw4w9WgXcQ  "))
        assertEquals("http://example.com/a.mp4", valid("http://example.com/a.mp4"))
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `blank input asks for a link`() {
        assertEquals("Paste a link first.", invalidReason(null))
        assertEquals("Paste a link first.", invalidReason("   "))
    }

    @Test
    fun `prose and multi-line text are rejected`() {
        assertTrue(invalidReason("check this out https://youtu.be/x").contains("spaces"))
        assertTrue(invalidReason("https://a.com/x\nhttps://b.com/y").contains("spaces"))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertEquals("Only http(s) links are supported.", invalidReason("ftp://example.com/a.mp4"))
        assertEquals("Only http(s) links are supported.", invalidReason("javascript:alert(1)"))
    }

    @Test
    fun `schemeless text gets a prefix hint`() {
        assertEquals("Links need an http:// or https:// prefix.", invalidReason("youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `local and private hosts are rejected`() {
        assertEquals("Local network links aren't supported.", invalidReason("https://localhost/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://router.local/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://127.0.0.1/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://10.0.0.5/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://192.168.1.10/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://172.16.4.4/x"))
        assertEquals("Local network links aren't supported.", invalidReason("https://[::1]/x"))
    }

    @Test
    fun `over long links are rejected`() {
        val long = "https://example.com/" + "a".repeat(UrlSecurity.MAX_URL_LENGTH)
        assertTrue(long.length > UrlSecurity.MAX_URL_LENGTH)
        assertTrue(invalidReason(long).contains("too long"))
    }

    @Test
    fun `extractFirstUrl pulls the link out of share prose`() {
        assertEquals("https://youtu.be/x", UrlSecurity.extractFirstUrl("Check this out: https://youtu.be/x 😀"))
        assertEquals("https://youtu.be/x", UrlSecurity.extractFirstUrl("(https://youtu.be/x)."))
        assertEquals("https://youtu.be/ok", UrlSecurity.extractFirstUrl("ftp://bad.example/x https://youtu.be/ok"))
        assertEquals("https://youtu.be/ok", UrlSecurity.extractFirstUrl("https://localhost/blocked https://youtu.be/ok"))
        assertNull(UrlSecurity.extractFirstUrl("no links here"))
        assertNull(UrlSecurity.extractFirstUrl(null))
    }
}
