package com.fetchly.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveErrorsTest {

    @Test
    fun `drm wins over sign in`() {
        val raw = "ERROR: [youtube] x: This video is DRM protected and requires authentication"
        assertEquals(
            "This media is DRM-protected, so it can't be downloaded.",
            ResolveErrors.messageFor(raw),
        )
    }

    @Test
    fun `age restriction wins over sign in`() {
        val raw = "ERROR: [youtube] x: Sign in to confirm your age. " +
            "This video may be inappropriate for some users."
        assertEquals(
            "This video is age-restricted. Fetchly can't sign in, so it can't download it.",
            ResolveErrors.messageFor(raw),
        )
    }

    @Test
    fun `bot challenge is not mislabeled as private media`() {
        assertEquals(
            "The source is asking for a bot check. Try again later or open the link in your browser.",
            ResolveErrors.messageFor("ERROR: [youtube] x: Sign in to confirm you're not a bot."),
        )
    }

    @Test
    fun `private and members only and authentication`() {
        val expected = "This media isn't publicly available. It needs a sign-in, or it's private."
        assertEquals(expected, ResolveErrors.messageFor("[youtube] x: Private video"))
        assertEquals(expected, ResolveErrors.messageFor("[youtube] x: This video is members-only"))
        assertEquals(expected, ResolveErrors.messageFor("ERROR: [generic] x: This request requires authentication"))
    }

    @Test
    fun `rate limiting gives an actionable explanation`() {
        assertEquals(
            "The source is limiting requests. Wait a little, then try again.",
            ResolveErrors.messageFor("ERROR: HTTP Error 429: Too Many Requests"),
        )
    }

    @Test
    fun `region lock`() {
        assertEquals(
            "The source says this media isn't available in your region.",
            ResolveErrors.messageFor(
                "ERROR: [youtube] x: The uploader has not made this video available in your country",
            ),
        )
    }

    @Test
    fun `unsupported source`() {
        assertEquals(
            "This source isn't supported yet.",
            ResolveErrors.messageFor("ERROR: Unsupported URL: https://example.invalid/foo"),
        )
    }

    @Test
    fun `removed media`() {
        val expected = "The source says this media doesn't exist or has been removed."
        assertEquals(expected, ResolveErrors.messageFor("ERROR: [youtube] x: Video unavailable"))
        assertEquals(expected, ResolveErrors.messageFor("ERROR: HTTP Error 404: Not Found"))
    }

    @Test
    fun `engine broke on a source change`() {
        assertEquals(
            "The source changed how it serves this media and Fetchly's engine couldn't read it. " +
                "Updating Fetchly usually fixes this.",
            ResolveErrors.messageFor(
                "ERROR: [youtube] x: Unable to extract player response; please report this issue",
            ),
        )
    }

    @Test
    fun `network trouble`() {
        val expected = "Couldn't reach the source. Check your connection and try again."
        assertEquals(
            expected,
            ResolveErrors.messageFor(
                "ERROR: unable to download video data: <urlopen error [Errno -3] " +
                    "Temporary failure in name resolution>",
            ),
        )
        assertEquals(expected, ResolveErrors.messageFor("java.net.ConnectException: Connection refused"))
    }

    @Test
    fun `fallback quotes the raw engine message trimmed to 160 chars`() {
        val message = ResolveErrors.messageFor("x".repeat(400))

        val prefix = "Fetchly couldn't read this link. The engine said: \""
        assertTrue(message.startsWith(prefix))
        assertTrue(message.endsWith("\""))
        assertEquals(160, message.removePrefix(prefix).removeSuffix("\"").length)
    }

    @Test
    fun `blank raw falls back to a short message`() {
        assertEquals("Fetchly couldn't read this link.", ResolveErrors.messageFor(null))
        assertEquals("Fetchly couldn't read this link.", ResolveErrors.messageFor("   "))
    }
}
