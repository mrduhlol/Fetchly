package com.fetchly.app.domain.util

import java.util.Calendar

enum class DayBucket { TODAY, YESTERDAY, EARLIER }

/** Human-friendly timestamps for history. Pure logic, unit-tested. */
object RelativeTime {

    fun timeAgo(nowMs: Long, thenMs: Long): String {
        val diff = (nowMs - thenMs).coerceAtLeast(0)
        val mins = diff / 60_000
        if (mins < 1) return "Just now"
        if (mins < 60) return "$mins min ago"
        val hours = mins / 60
        if (hours < 24) return "$hours hr ago"
        val days = hours / 24
        if (days == 1L) return "Yesterday"
        if (days < 7) return "$days days ago"
        return java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(thenMs))
    }

    fun bucket(nowMs: Long, thenMs: Long): DayBucket {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        return when {
            thenMs >= startOfToday -> DayBucket.TODAY
            thenMs >= startOfToday - 86_400_000L -> DayBucket.YESTERDAY
            else -> DayBucket.EARLIER
        }
    }
}
