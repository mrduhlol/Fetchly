package com.fetchly.app.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadRunGateTest {
    @Test fun `resume waits for cancelled attempt cleanup without blocking another download`() = runTest {
        val releaseCleanup = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch {
            DownloadRunGate.serial("same") {
                try { started.complete(Unit); CompletableDeferred<Unit>().await() }
                finally { withContext(NonCancellable) { releaseCleanup.await(); events += "cleaned" } }
            }
        }
        started.await()
        first.cancel()
        val resumed = launch { DownloadRunGate.serial("same") { events += "resumed" } }
        val other = launch { DownloadRunGate.serial("other") { events += "other" } }
        runCurrent()
        assertEquals(listOf("other"), events)
        releaseCleanup.complete(Unit)
        first.join(); resumed.join(); other.join()
        assertEquals(listOf("other", "cleaned", "resumed"), events)
    }
}
