package com.fetchly.app.domain.util

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    /** "just now", "5 min ago", "3 h ago", "2 d ago", "1 w ago". */
    fun format(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - thenMs).coerceAtLeast(0)
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            diff < DAY -> "${diff / HOUR} h ago"
            diff < 7 * DAY -> "${diff / DAY} d ago"
            else -> "${diff / (7 * DAY)} w ago"
        }
    }
}
