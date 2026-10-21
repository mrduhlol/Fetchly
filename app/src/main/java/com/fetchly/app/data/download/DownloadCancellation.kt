package com.fetchly.app.data.download

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Interrupt blocking engines as soon as their parent is cancelled, before joining them. */
internal suspend fun <T> withDownloadCancellation(
    interrupt: () -> Unit,
    block: suspend () -> T,
): T = coroutineScope {
    val observer = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            interrupt()
        }
    }
    try {
        block()
    } finally {
        observer.cancel()
    }
}
