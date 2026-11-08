package com.fetchly.app.data.download

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A resumed worker must wait until the previous attempt has finished cleaning its scratch files. */
internal object DownloadRunGate {
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val gates = mutableMapOf<String, Gate>()

    suspend fun <T> serial(id: String, work: suspend () -> T): T {
        val gate = synchronized(gates) { gates.getOrPut(id) { Gate() }.also { it.users++ } }
        try {
            return gate.mutex.withLock { work() }
        } finally {
            synchronized(gates) { if (--gate.users == 0) gates.remove(id) }
        }
    }
}
