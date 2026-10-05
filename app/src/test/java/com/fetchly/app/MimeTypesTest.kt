package com.fetchly.app

import com.fetchly.app.domain.util.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class MimeTypesTest {

    @Test fun mapsVideoContainers() {
        assertEquals("video/mp4", MimeTypes.fromContainer("mp4"))
        assertEquals("video/mp4", MimeTypes.fromContainer("MP4"))
        assertEquals("video/webm", MimeTypes.fromContainer("webm"))
    }

    @Test fun mapsAudioContainers() {
        assertEquals("audio/mpeg", MimeTypes.fromContainer("mp3"))
        assertEquals("audio/mp4", MimeTypes.fromContainer("m4a"))
    }

    @Test fun mapsImageContainers() {
        assertEquals("image/jpeg", MimeTypes.fromContainer("jpg"))
        assertEquals("image/png", MimeTypes.fromContainer("png"))
        assertEquals("image/webp", MimeTypes.fromContainer("webp"))
    }

    @Test fun fallsBackForUnknown() {
        assertEquals("application/octet-stream", MimeTypes.fromContainer("xyz"))
    }

    @Test fun formatsSizes() {
        assertEquals("Unknown size", MimeTypes.displaySize(null))
        assertEquals("512 B", MimeTypes.displaySize(512))
        assertEquals("45 MB", MimeTypes.displaySize(45L * 1024 * 1024))
        assertEquals("8.4 MB", MimeTypes.displaySize((8.4 * 1024 * 1024).toLong()))
    }
}
