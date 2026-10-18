package com.fetchly.app.domain.util

import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenamesTest {

    private val stream = StreamRef(
        formatId = "137",
        url = "https://cdn.example.invalid/137",
        ext = "mp4",
        vcodec = "avc1.640028",
        acodec = "none",
        height = 1080,
        abr = null,
        sizeBytes = 80_911_999L,
        protocol = "https",
    )

    private fun option(label: String, container: String) = QualityOption(
        id = "opt",
        label = label,
        kind = QualityKind.VIDEO,
        container = container,
        streams = listOf(stream),
        needsMerge = true,
        sizeBytes = null,
    )

    @Test
    fun `path and control characters become spaces`() {
        assertEquals("Rock Paper Scissors", Filenames.sanitize("Rock/Paper\\Scissors:*?"))
        assertEquals("a b", Filenames.sanitize("a\u0000b"))
    }

    @Test
    fun `whitespace collapses and edges are trimmed`() {
        assertEquals("My Video", Filenames.sanitize("  My   Video  "))
        assertEquals("video", Filenames.sanitize("video..."))
    }

    @Test
    fun `unusable titles fall back to a generic stem`() {
        assertEquals("Fetchly media", Filenames.sanitize(""))
        assertEquals("Fetchly media", Filenames.sanitize("///"))
        assertEquals("Fetchly media", Filenames.sanitize("   ...   "))
    }

    @Test
    fun `very long titles are capped`() {
        assertTrue(Filenames.sanitize("x".repeat(500)).length <= 80)
    }

    @Test
    fun `display name carries the quality and container`() {
        assertEquals("My Video [1080p].mp4", Filenames.displayName("My Video", option("1080p", "mp4")))
        assertEquals("My Video [1080p].mp4", Filenames.displayName("My Video", "1080p", "MP4"))
    }

    @Test
    fun `temp prefix is stable and filesystem safe`() {
        assertEquals("fetchly_12345678-90ab-cdef", Filenames.tempPrefix("12345678-90ab-cdef"))
        assertEquals(Filenames.tempPrefix("abc"), Filenames.tempPrefix("abc"))
        // A hostile id can't escape the temp dir.
        assertEquals("fetchly_evil", Filenames.tempPrefix("../evil"))
    }
}
