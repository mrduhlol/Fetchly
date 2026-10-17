package com.fetchly.app.data.engine

import java.util.concurrent.atomic.AtomicBoolean

/** Bytes and speed as reported by yt-dlp. Values <= 0 mean "unknown". */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Double,
)

/**
 * Progress bridge called FROM Python (Chaquopy) while yt-dlp downloads.
 *
 * Python raises its cancel exception as soon as [shouldCancel] turns true, so
 * a cancel request stops the transfer within one chunk. [update] is throttled
 * on the Python side (~5 calls/second), so [onProgress] stays cheap; it runs
 * on the download thread, so callers hop to their own dispatcher if needed.
 */
class DownloadHook(private val onProgress: (DownloadProgress) -> Unit) {

    private val cancelled = AtomicBoolean(false)

    /** Called from Python with raw byte counts (0 when unknown). */
    fun update(downloadedBytes: Long, totalBytes: Long, speedBytesPerSecond: Double) {
        if (cancelled.get()) return
        onProgress(
            DownloadProgress(
                downloadedBytes = downloadedBytes.coerceAtLeast(0),
                totalBytes = totalBytes.coerceAtLeast(0),
                speedBytesPerSecond = speedBytesPerSecond.coerceAtLeast(0.0),
            )
        )
    }

    /** Called from Python on every progress callback. */
    fun shouldCancel(): Boolean = cancelled.get()

    /** Called from the app side; takes effect on the next chunk. */
    fun requestCancel() {
        cancelled.set(true)
    }
}
