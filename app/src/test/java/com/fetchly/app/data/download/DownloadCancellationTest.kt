package com.fetchly.app.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCancellationTest {
    @Test fun `cancellation interrupts before waiting for a blocking engine to exit`() = runTest {
        val started = CompletableDeferred<Unit>()
        val interrupted = CompletableDeferred<Unit>()
        val job = launch {
            withDownloadCancellation(interrupt = { interrupted.complete(Unit) }) {
                // Models a native operation which cannot finish until explicitly interrupted.
                withContext(NonCancellable) {
                    started.complete(Unit)
                    interrupted.await()
                }
            }
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(interrupted.isCompleted)
        assertTrue(job.isCancelled)
    }

    @Test fun `normal completion returns its result and releases the observer`() = runTest {
        var cleanedUp = false
        assertEquals("saved", withDownloadCancellation(interrupt = { cleanedUp = true }) { "saved" })
        assertTrue(cleanedUp)
    }
}
