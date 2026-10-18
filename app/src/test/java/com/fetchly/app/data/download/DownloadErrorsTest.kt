package com.fetchly.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorsTest {

    @Test
    fun `running out of storage is named first`() {
        val diskFull = "Not enough free storage for this download."
        assertEquals(diskFull, DownloadErrors.textFor("No space left on device"))
        assertEquals(diskFull, DownloadErrors.textFor("OSError: [Errno 28] No space left on device"))
        assertEquals(diskFull, DownloadErrors.textFor("ENOSPC"))
        // Even when the failure also mentions the merge step, storage wins:
        // "ffmpeg couldn't write the file" is not a merge problem.
        assertEquals(diskFull, DownloadErrors.textFor("ffmpeg: No space left on device"))
    }

    @Test
    fun `merge failures admit the parts arrived but not the whole`() {
        val mergeFailed = "The media was downloaded but couldn't be assembled (merge failed)."
        assertEquals(mergeFailed, DownloadErrors.textFor("ffmpeg exited with code 1"))
        assertEquals(mergeFailed, DownloadErrors.textFor("Failed to mux streams"))
        assertEquals(mergeFailed, DownloadErrors.textFor("merge failed"))
    }

    @Test
    fun `expired links are surfaced only after the retry also failed`() {
        val expired = "The download link expired and even a fresh one was refused. Try again later."
        assertEquals(expired, DownloadErrors.textFor("HTTP Error 403: Forbidden"))
        assertEquals(expired, DownloadErrors.textFor("HTTP Error 410: Gone"))
        // The 403 wins over the connection wording it arrived wrapped in.
        assertEquals(expired, DownloadErrors.textFor("connection reset while fetching: 403"))
    }

    @Test
    fun `network trouble keeps its own sentence`() {
        val network = "The download lost its connection. Check your network and try again."
        assertEquals(network, DownloadErrors.textFor("Connection timed out"))
        assertEquals(network, DownloadErrors.textFor("getaddrinfo failed"))
        assertEquals(network, DownloadErrors.textFor("Connection reset by peer"))
        assertEquals(network, DownloadErrors.textFor("EOF occurred in violation of protocol"))
        assertEquals(network, DownloadErrors.textFor("the proxy refused the tunnel"))
    }

    @Test
    fun `empty raw text admits the download never started`() {
        val neverStarted = "The download failed before it started."
        assertEquals(neverStarted, DownloadErrors.textFor(null))
        assertEquals(neverStarted, DownloadErrors.textFor(""))
        assertEquals(neverStarted, DownloadErrors.textFor("  \n\t "))
    }

    @Test
    fun `anything else quotes the engine verbatim`() {
        assertEquals(
            "The download failed. The engine said: \"weird engine explosion\"",
            DownloadErrors.textFor("weird   engine\n\nexplosion"),
        )
    }

    @Test
    fun `the quoted fallback is whitespace collapsed and capped`() {
        val text = DownloadErrors.textFor("  " + "x".repeat(500) + "  ")
        val prefix = "The download failed. The engine said: \""
        assertTrue(text.startsWith(prefix))
        assertTrue(text.endsWith("\""))
        assertEquals(prefix.length + 160 + 1, text.length)
    }

    @Test
    fun `classifiers say what they mean`() {
        assertTrue(DownloadErrors.isExpiredLink("HTTP 403: Forbidden"))
        assertTrue(DownloadErrors.isExpiredLink("410 gone"))
        assertFalse(DownloadErrors.isExpiredLink(null))
        assertFalse(DownloadErrors.isExpiredLink("connection reset"))

        assertTrue(DownloadErrors.isNetworkProblem("Connection reset by peer"))
        assertTrue(DownloadErrors.isNetworkProblem("temporary failure in name resolution"))
        assertFalse(DownloadErrors.isNetworkProblem("HTTP 403"))
        assertFalse(DownloadErrors.isNetworkProblem(null))
    }
}
