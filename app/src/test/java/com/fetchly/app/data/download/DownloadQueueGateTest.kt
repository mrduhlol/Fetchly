package com.fetchly.app.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadQueueGateTest {
    @Test fun `resume cannot schedule replacement until pause cancellation finishes`() = runTest {
        val gate = DownloadQueueGate()
        val finishCancellation = CompletableDeferred<Unit>()
        val paused = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val pause = launch {
            gate.serial {
                events += "paused"
                paused.complete(Unit)
                finishCancellation.await()
                events += "old work cancelled"
            }
        }
        paused.await()
        val resume = launch { gate.serial { events += "replacement scheduled" } }
        runCurrent()
        assertEquals(listOf("paused"), events)
        finishCancellation.complete(Unit)
        pause.join()
        resume.join()
        assertEquals(listOf("paused", "old work cancelled", "replacement scheduled"), events)
    }

    @Test fun `caller cancellation still completes committed transition before next command`() = runTest {
        val gate = DownloadQueueGate()
        val committed = CompletableDeferred<Unit>()
        val finishCancellation = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val pause = launch {
            gate.serial {
                committed.complete(Unit)
                finishCancellation.await()
                events += "cancelled old work"
                events += "pumped queue"
            }
        }
        committed.await()
        pause.cancel()
        val resume = launch { gate.serial { events += "resumed" } }
        runCurrent()
        assertFalse(pause.isCompleted)
        assertEquals(emptyList<String>(), events)
        finishCancellation.complete(Unit)
        pause.join()
        resume.join()
        assertEquals(listOf("cancelled old work", "pumped queue", "resumed"), events)
    }
}
