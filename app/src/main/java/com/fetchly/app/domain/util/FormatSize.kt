package com.fetchly.app.domain.util

import java.util.Locale

/**
 * Human-readable sizes. `null` in -> `null` out: unknown sizes are never
 * invented anywhere in Fetchly.
 */
object FormatSize {
    /** "84.0 MB", "1.5 KB", "512 B" — or null when the size is unknown. */
    fun format(bytes: Long?): String? = bytes?.let(::formatKnown)

    /** For estimates rather than exact totals ("~84.0 MB"). */
    fun formatApprox(bytes: Long?): String? = format(bytes)?.let { "~$it" }

    private fun formatKnown(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
