package com.fetchly.app

import com.fetchly.app.domain.util.DayBucket
import com.fetchly.app.domain.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RelativeTimeTest {

    @Test fun justNow() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", RelativeTime.timeAgo(now, now - 30_000))
    }

    @Test fun minutesAndHours() {
        val now = System.currentTimeMillis()
        assertEquals("5 min ago", RelativeTime.timeAgo(now, now - 5 * 60_000))
        assertEquals("3 hr ago", RelativeTime.timeAgo(now, now - 3 * 3_600_000))
    }

    @Test fun bucketsDays() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        val noon = cal.timeInMillis
        val moment = if (noon > now) noon - 3_600_000 else noon
        assertEquals(DayBucket.TODAY, RelativeTime.bucket(now, moment))
        assertEquals(DayBucket.YESTERDAY, RelativeTime.bucket(now, moment - 86_400_000L))
        assertEquals(DayBucket.EARLIER, RelativeTime.bucket(now, moment - 10 * 86_400_000L))
    }
}
