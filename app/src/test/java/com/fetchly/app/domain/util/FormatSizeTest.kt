package com.fetchly.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSizeTest {

    @Test
    fun `null stays null - sizes are never invented`() {
        assertNull(FormatSize.format(null))
        assertNull(FormatSize.formatApprox(null))
    }

    @Test
    fun `bytes below a kilobyte show as bytes`() {
        assertEquals("0 B", FormatSize.format(0))
        assertEquals("512 B", FormatSize.format(512))
        assertEquals("1023 B", FormatSize.format(1023))
    }

    @Test
    fun `kilobytes have one decimal`() {
        assertEquals("1.0 KB", FormatSize.format(1024))
        assertEquals("1.5 KB", FormatSize.format(1536))
    }

    @Test
    fun `megabytes have one decimal`() {
        assertEquals("2.4 MB", FormatSize.format(2_500_000))
        assertEquals("84.0 MB", FormatSize.format(88_100_000))
    }

    @Test
    fun `gigabytes have two decimals`() {
        assertEquals("1.50 GB", FormatSize.format(1_610_612_736))
    }

    @Test
    fun `approx prefixes a tilde`() {
        assertEquals("~84.0 MB", FormatSize.formatApprox(88_100_000))
    }
}
