package com.fetchly.app.domain.resolve

import com.fetchly.app.data.repository.MediaRepository
import com.fetchly.app.data.repository.ResolveOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveUrlUseCaseTest {

    private class RecordingRepository : MediaRepository {
        var calls = 0
        var lastUrl: String? = null

        override suspend fun analyze(url: String): ResolveOutcome {
            calls++
            lastUrl = url
            return ResolveOutcome.Failed("reached the repository", raw = null)
        }
    }

    @Test
    fun `invalid urls fail locally and never reach the repository`() = runBlocking {
        val repository = RecordingRepository()

        val outcome = ResolveUrlUseCase(repository)("not a link at all")

        assertTrue(outcome is ResolveOutcome.Failed)
        val failed = outcome as ResolveOutcome.Failed
        assertEquals("That doesn't look like a link — it contains spaces.", failed.message)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `valid urls are trimmed and passed through`() = runBlocking {
        val repository = RecordingRepository()

        ResolveUrlUseCase(repository)("  https://youtu.be/test123  ")

        assertEquals(1, repository.calls)
        assertEquals("https://youtu.be/test123", repository.lastUrl)
    }
}
