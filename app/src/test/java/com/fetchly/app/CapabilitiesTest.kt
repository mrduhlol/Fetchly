package com.fetchly.app

import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.platform.SourceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    @Test fun directLinksAreSupported() {
        val cap = SourceCapabilities.forPlatform(Platform.DIRECT)
        assertTrue(cap.supported)
        assertTrue(cap.supportsVideo)
        assertTrue(cap.supportsAudio)
        assertTrue(cap.supportsImage)
    }

    @Test fun youTubeIsSupportedThroughEngine() {
        val cap = SourceCapabilities.forPlatform(Platform.YOUTUBE)
        assertTrue(cap.supported)
        assertTrue(cap.supportsQualitySelection)
        assertTrue(cap.supportsThumbnail)
    }

    @Test fun socialPlatformsAreHonestlyUnsupported() {
        for (p in listOf(
            Platform.INSTAGRAM, Platform.TIKTOK,
            Platform.TWITTER, Platform.REDDIT, Platform.FACEBOOK, Platform.PINTEREST,
        )) {
            val cap = SourceCapabilities.forPlatform(p)
            assertFalse("expected $p unsupported", cap.supported)
        }
    }

    @Test fun detectorHintsDirectFiles() {
        assertEquals(
            Platform.DIRECT,
            PlatformDetector.detectWithDirect("https://cdn.example.com/clip.mp4"),
        )
        assertEquals(
            Platform.DIRECT,
            PlatformDetector.detectWithDirect("https://cdn.example.com/song.mp3"),
        )
        assertEquals(
            Platform.YOUTUBE,
            PlatformDetector.detectWithDirect("https://www.youtube.com/watch?v=abc"),
        )
        assertEquals(
            Platform.UNKNOWN,
            PlatformDetector.detectWithDirect("https://example.com/page"),
        )
    }
}
