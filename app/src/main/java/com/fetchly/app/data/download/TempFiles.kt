package com.fetchly.app.data.download

import android.content.Context
import java.io.File

/**
 * Scratch space for downloads: `cacheDir/fetchly`. Everything under it is
 * disposable — the worker removes a download's own files when it finishes,
 * and [cleanupStale] sweeps anything a kill left behind.
 */
object TempFiles {

    /** Anything older than this is assumed abandoned (a kill, a crash). */
    const val DEFAULT_MAX_AGE_MS = 48L * 60 * 60 * 1000

    fun dir(context: Context): File = File(context.cacheDir, "fetchly")

    /**
     * Deletes entries under [dir] whose last-modified time is older than
     * [maxAgeMs], recursively. Returns how many top-level entries went away.
     *
     * [now] is a parameter so the age sweep can be tested without waiting.
     */
    fun cleanupStale(
        dir: File,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val entries = dir.listFiles() ?: return 0
        var removed = 0
        for (entry in entries) {
            if (now - entry.lastModified() > maxAgeMs && entry.deleteRecursively()) {
                removed++
            }
        }
        return removed
    }
}
