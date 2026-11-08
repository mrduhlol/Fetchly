package com.fetchly.app.data.download

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keep a history transition and its WorkManager operation together, including on UI dismissal. */
internal class DownloadQueueGate {
    private val mutex = Mutex()

    suspend fun <T> serial(command: suspend () -> T): T = mutex.withLock {
        withContext(NonCancellable) { command() }
    }
}
