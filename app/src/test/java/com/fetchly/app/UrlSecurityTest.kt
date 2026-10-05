package com.fetchly.app

import com.fetchly.app.domain.security.UrlSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSecurityTest {

    @Test fun acceptsHttps() {
        assertTrue(UrlSecurity.validate("https://example.com/media").isSuccess)
    }

    @Test fun addsScheme() {
        assertEquals("https://example.com/x", UrlSecurity.validate("example.com/x").getOrThrow())
    }

    @Test fun rejectsEmpty() {
        assertTrue(UrlSecurity.validate("   ").isFailure)
    }

    @Test fun sanitizesFileName() {
        assertEquals("a_b_c", UrlSecurity.sanitizeFileName("a/b:c*?\"<>|"))
        assertEquals("fetchly_media", UrlSecurity.sanitizeFileName("   "))
    }
}
