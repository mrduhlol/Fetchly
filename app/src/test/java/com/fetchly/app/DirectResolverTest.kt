package com.fetchly.app

import com.fetchly.app.data.resolve.DirectMediaResolver
import com.fetchly.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectResolverTest {

    @Test fun classifiesMediaMimes() {
        assertTrue(DirectMediaResolver.isMedia("video/mp4"))
        assertTrue(DirectMediaResolver.isMedia("audio/mpeg; charset=binary"))
        assertTrue(DirectMediaResolver.isMedia("image/jpeg"))
        assertFalse(DirectMediaResolver.isMedia("text/html"))
        assertFalse(DirectMediaResolver.isMedia("application/json"))
    }

    @Test fun mapsMimeToType() {
        assertEquals(MediaType.VIDEO, DirectMediaResolver.mediaTypeForMime("video/mp4"))
        assertEquals(MediaType.AUDIO, DirectMediaResolver.mediaTypeForMime("audio/mpeg"))
        assertEquals(MediaType.IMAGE, DirectMediaResolver.mediaTypeForMime("image/png"))
        assertNull(DirectMediaResolver.mediaTypeForMime("text/html"))
    }

    @Test fun mapsMimeToContainer() {
        assertEquals("mp3", DirectMediaResolver.containerForMime("audio/mpeg", "https://x/y"))
        assertEquals("jpg", DirectMediaResolver.containerForMime("image/jpeg", "https://x/y"))
        assertEquals("mp4", DirectMediaResolver.containerForMime("video/mp4", "https://x/y"))
    }

    @Test fun extractsTitleFromPath() {
        assertEquals(
            "funny cats",
            DirectMediaResolver.titleFromUrl("https://cdn.example.com/clips/funny%20cats.mp4", null),
        )
        assertEquals(
            "song",
            DirectMediaResolver.titleFromUrl("https://cdn.example.com/song.mp3?token=abc", null),
        )
    }

    @Test fun prefersContentDisposition() {
        assertEquals(
            "My Video",
            DirectMediaResolver.titleFromUrl(
                "https://cdn.example.com/dl?id=9",
                "attachment; filename=\"My Video.mp4\"",
            ),
        )
    }

    @Test fun extensionFromPath() {
        assertEquals("mp4", DirectMediaResolver.extensionFromPath("https://x.com/a/video.mp4"))
        assertEquals("", DirectMediaResolver.extensionFromPath("https://x.com/a/video"))
    }
}
