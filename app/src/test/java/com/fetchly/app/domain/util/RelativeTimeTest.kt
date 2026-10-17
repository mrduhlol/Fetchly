package com.fetchly.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_800_000_000_000L // fixed clock: deterministic tests

    @Test
    fun `recent moments say just now`() {
        assertEquals("just now", RelativeTime.format(now, now))
        assertEquals("just now", RelativeTime.format(now - 59_000, now))
    }

    @Test
    fun `clock skew in the future still reads just now`() {
        assertEquals("just now", RelativeTime.format(now + 30_000, now))
    }

    @Test
    fun `minutes hours days weeks`() {
        assertEquals("1 min ago", RelativeTime.format(now - 60_000, now))
        assertEquals("5 min ago", RelativeTime.format(now - 5 * 60_000, now))
        assertEquals("1 h ago", RelativeTime.format(now - 3_600_000, now))
        assertEquals("3 h ago", RelativeTime.format(now - 3 * 3_600_000, now))
        assertEquals("2 d ago", RelativeTime.format(now - 2 * 86_400_000L, now))
        assertEquals("1 w ago", RelativeTime.format(now - 8 * 86_400_000L, now))
        assertEquals("5 w ago", RelativeTime.format(now - 35 * 86_400_000L, now))
    }
}
