package com.fetchly.app.data.download

import com.fetchly.app.domain.util.MediaSniffer
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class BulkArchiveTest {
    @Test fun `archive preserves each media file and has a valid ZIP signature`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val video = File(dir, "video.mp4").apply { writeBytes(ByteArray(10000) { (it % 251).toByte() }) }
            val photo = File(dir, "photo.jpg").apply { writeBytes(ByteArray(120) { 42 }) }
            val output = File(dir, "post.zip")
            BulkArchive.write(listOf(video, photo), output)
            assertTrue(MediaSniffer.matches(output, "zip"))
            ZipFile(output).use { zip ->
                assertEquals(listOf("media-01.mp4", "media-02.jpg"), zip.entries().toList().map { it.name })
                assertArrayEquals(video.readBytes(), zip.getInputStream(zip.getEntry("media-01.mp4")).readBytes())
                assertArrayEquals(photo.readBytes(), zip.getInputStream(zip.getEntry("media-02.jpg")).readBytes())
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun `cancelled archive leaves no incomplete output`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val input = File(dir, "video.mp4").apply { writeBytes(ByteArray(100)) }
            val output = File(dir, "post.zip")
            try {
                BulkArchive.write(listOf(input), output) { throw java.util.concurrent.CancellationException() }
                fail("Expected cancellation")
            } catch (_: java.util.concurrent.CancellationException) { assertFalse(output.exists()) }
        } finally { dir.deleteRecursively() }
    }
}
