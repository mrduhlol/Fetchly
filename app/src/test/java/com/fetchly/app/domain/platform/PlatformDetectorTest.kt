package com.fetchly.app.domain.platform

import com.fetchly.app.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun `youtube url forms`() {
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://youtube.com/shorts/abc123"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://music.youtube.com/watch?v=x"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://m.youtube.com/watch?v=x"))
    }

    @Test
    fun `social hosts`() {
        assertEquals(Platform.LINKEDIN, PlatformDetector.detect("https://www.linkedin.com/posts/user_activity-123"))
        assertEquals(Platform.LINKEDIN, PlatformDetector.detect("https://lnkd.in/abc"))
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reel/Cxyz/"))
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@user/video/123"))
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://x.com/user/status/123"))
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://twitter.com/user/status/123"))
        assertEquals(Platform.REDDIT, PlatformDetector.detect("https://www.reddit.com/r/aww/comments/xyz"))
        assertEquals(Platform.REDDIT, PlatformDetector.detect("https://redd.it/abc"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/watch/?v=1"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://fb.watch/abc/"))
        assertEquals(Platform.PINTEREST, PlatformDetector.detect("https://www.pinterest.com/pin/123/"))
        assertEquals(Platform.PINTEREST, PlatformDetector.detect("https://pin.it/abc"))
    }

    @Test
    fun `host matching is case insensitive`() {
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("HTTPS://WWW.YouTube.COM/watch?v=x"))
    }

    @Test
    fun `media extensions mean a direct file`() {
        assertEquals(Platform.DIRECT, PlatformDetector.detect("https://cdn.example.com/videos/clip.mp4"))
        assertEquals(Platform.DIRECT, PlatformDetector.detect("https://files.example.com/song.mp3?token=abc"))
        assertEquals(Platform.DIRECT, PlatformDetector.detect("https://images.example.com/photo.JPG"))
    }

    @Test
    fun `everything else is other`() {
        assertEquals(Platform.OTHER, PlatformDetector.detect("https://example.com/page.html"))
        assertEquals(Platform.OTHER, PlatformDetector.detect("https://example.com/"))
        assertEquals(Platform.OTHER, PlatformDetector.detect("not a url"))
    }

    @Test
    fun `capability gives youtube and direct first-class treatment`() {
        assertEquals(Capability.SUPPORTED, PlatformDetector.capability(Platform.YOUTUBE))
        assertEquals(Capability.SUPPORTED, PlatformDetector.capability(Platform.DIRECT))
        assertEquals(Capability.BEST_EFFORT, PlatformDetector.capability(Platform.INSTAGRAM))
        assertEquals(Capability.BEST_EFFORT, PlatformDetector.capability(Platform.OTHER))
    }
}
