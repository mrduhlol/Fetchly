package com.fetchly.app.data.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TempFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `stale entries are swept and fresh ones stay`() {
        val dir = folder.newFolder("fetchly")
        val now = 10_000_000L
        val stale = file(dir, "old.part", now - 5_000)
        val fresh = file(dir, "new.part", now - 1_000)

        val removed = TempFiles.cleanupStale(dir, maxAgeMs = 2_000, now = now)

        assertEquals(1, removed)
        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `stale directories go away with their contents`() {
        val dir = folder.newFolder("fetchly")
        val now = 10_000_000L
        val job = File(dir, "fetchly_job").apply { mkdirs() }
        File(job, "video.f137.mp4").writeText("video bytes")
        File(job, "audio.f140.m4a").writeText("audio bytes")
        job.setLastModified(now - 5_000)

        val removed = TempFiles.cleanupStale(dir, maxAgeMs = 2_000, now = now)

        assertEquals(1, removed)
        assertFalse(job.exists())
    }

    @Test
    fun `the age limit is strict`() {
        val dir = folder.newFolder("fetchly")
        val entry = File(dir, "exactly.part").apply { writeText("x") }
        // Read the timestamp back so filesystem rounding can't skew the math.
        val modifiedAt = entry.lastModified()

        assertEquals(0, TempFiles.cleanupStale(dir, maxAgeMs = 1_000, now = modifiedAt + 1_000))
        assertTrue(entry.exists())

        assertEquals(1, TempFiles.cleanupStale(dir, maxAgeMs = 1_000, now = modifiedAt + 1_001))
        assertFalse(entry.exists())
    }

    @Test
    fun `a missing scratch dir is not an error`() {
        assertEquals(
            0,
            TempFiles.cleanupStale(File(folder.root, "never-created"), maxAgeMs = 1, now = 2),
        )
    }

    @Test
    fun `sweeping never removes the scratch dir itself`() {
        val dir = folder.newFolder("fetchly")
        file(dir, "old.part", 5_000L)

        assertEquals(1, TempFiles.cleanupStale(dir, maxAgeMs = 1_000, now = 10_000_000L))

        assertTrue(dir.isDirectory)
        assertEquals(0, dir.listFiles()!!.size)
    }

    private fun file(dir: File, name: String, modifiedAt: Long): File =
        File(dir, name).apply {
            writeText("stale bytes")
            setLastModified(modifiedAt)
        }
}
