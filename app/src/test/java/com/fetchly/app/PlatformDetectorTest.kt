package com.fetchly.app

import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {

    @Test fun detectsYouTube() {
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://www.youtube.com/watch?v=abc"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://youtu.be/abc"))
    }

    @Test fun detectsInstagram() {
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/p/abc/"))
    }

    @Test fun detectsTikTok() {
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@u/video/123"))
    }

    @Test fun detectsTwitter() {
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://x.com/user/status/123"))
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://twitter.com/user/status/123"))
    }

    @Test fun detectsReddit() {
        assertEquals(Platform.REDDIT, PlatformDetector.detect("https://www.reddit.com/r/a/comments/1/"))
    }

    @Test fun detectsFacebook() {
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/watch/?v=1"))
    }

    @Test fun detectsPinterest() {
        assertEquals(Platform.PINTEREST, PlatformDetector.detect("https://www.pinterest.com/pin/1/"))
    }

    @Test fun unknownForOthers() {
        assertEquals(Platform.UNKNOWN, PlatformDetector.detect("https://example.com/video"))
        assertEquals(Platform.UNKNOWN, PlatformDetector.detect("not a url"))
    }
}
