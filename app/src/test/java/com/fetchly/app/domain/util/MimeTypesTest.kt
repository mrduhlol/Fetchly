package com.fetchly.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypesTest {

    @Test
    fun `containers map to media mime types`() {
        assertEquals("video/mp4", MimeTypes.forContainer("mp4"))
        assertEquals("video/x-matroska", MimeTypes.forContainer("mkv"))
        assertEquals("video/webm", MimeTypes.forContainer("webm"))
        assertEquals("audio/mp4", MimeTypes.forContainer("m4a"))
        assertEquals("audio/mpeg", MimeTypes.forContainer("mp3"))
        assertEquals("audio/ogg", MimeTypes.forContainer("opus"))
        assertEquals("image/jpeg", MimeTypes.forContainer("jpg"))
        assertEquals("image/webp", MimeTypes.forContainer("webp"))
        assertEquals("application/octet-stream", MimeTypes.forContainer("weird"))
    }

    @Test
    fun `audio webm stays audio`() {
        assertEquals("audio/webm", MimeTypes.forContainer("webm", audio = true))
        assertEquals("video/webm", MimeTypes.forContainer("WEBM", audio = false))
    }

    @Test
    fun `media mime detection follows the actual content type`() {
        assertTrue(MimeTypes.isMediaMime("video/mp4"))
        assertTrue(MimeTypes.isMediaMime("audio/mpeg"))
        assertTrue(MimeTypes.isMediaMime("image/png"))
        assertTrue(MimeTypes.isMediaMime("application/octet-stream"))
        assertTrue(MimeTypes.isMediaMime("video/mp4; codecs=avc1"))
        assertFalse(MimeTypes.isMediaMime("text/html"))
        assertFalse(MimeTypes.isMediaMime("application/json"))
        assertFalse(MimeTypes.isMediaMime(null))
    }
}
