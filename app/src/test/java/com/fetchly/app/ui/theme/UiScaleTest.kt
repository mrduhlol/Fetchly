package com.fetchly.app.ui.theme

import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    // A platform-like non-linear font curve: large headings grow less than body copy.
    private class PlatformDensity(override val density: Float = 3f) : Density {
        override val fontScale = 1.5f
        override fun TextUnit.toDp(): Dp =
            (if (value <= 20f) value * 1.5f else 30f + (value - 20f) * 1.1f).dp
        override fun Dp.toSp(): TextUnit =
            (if (value <= 30f) value / 1.5f else 20f + (value - 30f) / 1.1f).sp
    }

    @Test fun `app layout units use 80 percent of physical device density`() {
        for (deviceDensity in listOf(1f, 2f, 3.5f)) {
            val platform = PlatformDensity(deviceDensity)
            val app = ScaledDensity(platform)
            val original = with(platform) { 40.dp.toPx() }
            assertEquals(original * 0.8f, with(app) { 40.dp.toPx() }, 0.001f)
        }
    }

    @Test fun `supported scale choices are exactly 80 100 and 120 percent`() {
        assertEquals(listOf(80, 100, 120), UiScale.entries.map { it.percent })
        assertEquals(UiScale.COMPACT, UiScale.fromPercent(null))
        assertEquals(UiScale.COMPACT, UiScale.fromPercent(80))
        assertEquals(UiScale.STANDARD, UiScale.fromPercent(100))
        assertEquals(UiScale.LARGE, UiScale.fromPercent(120))
        assertEquals(UiScale.COMPACT, UiScale.fromPercent(90))
    }

    @Test fun `density scaling applies each supported preset`() {
        val platform = PlatformDensity()
        for (choice in UiScale.entries) {
            val app = ScaledDensity(platform, choice.factor)
            assertEquals(with(platform) { 40.dp.toPx() } * choice.factor,
                with(app) { 40.dp.toPx() }, 0.001f)
        }
    }

    @Test fun `body and headline text retain the platform font conversion curve`() {
        val platform = PlatformDensity()
        val app = ScaledDensity(platform)
        assertEquals(platform.fontScale, app.fontScale, 0f)
        for (font in listOf(16.sp, 40.sp)) {
            assertEquals(with(platform) { font.toDp() }, with(app) { font.toDp() })
            assertEquals(with(platform) { font.toPx() } * 0.8f, with(app) { font.toPx() }, 0.001f)
            assertEquals(font.value, with(app) { font.toDp().toSp().value }, 0.001f)
        }
    }

    @Test fun `physical viewport converts to more app space without breaking coordinate round trips`() {
        val app = ScaledDensity(PlatformDensity(2f))
        assertEquals(450f, with(app) { 720.toDp().value }, 0.001f)
        assertEquals(720f, with(app) { 720.toDp().toPx() }, 0.001f)
    }

    @Test fun `touch targets remain the same physical size and gesture settings are preserved`() {
        val platform = PlatformDensity(2f)
        val config = object : ViewConfiguration {
            override val longPressTimeoutMillis = 500L
            override val doubleTapTimeoutMillis = 300L
            override val doubleTapMinTimeMillis = 40L
            override val touchSlop = 16f
            override val minimumTouchTargetSize = DpSize(48.dp, 48.dp)
        }
        val app = ScaledDensity(platform)
        val scaledConfig = ScaledViewConfiguration(config)
        assertEquals(with(platform) { config.minimumTouchTargetSize.width.toPx() },
            with(app) { scaledConfig.minimumTouchTargetSize.width.toPx() }, 0.001f)
        assertEquals(config.touchSlop, scaledConfig.touchSlop, 0f)
        assertEquals(config.longPressTimeoutMillis, scaledConfig.longPressTimeoutMillis)
        assertEquals(config.doubleTapTimeoutMillis, scaledConfig.doubleTapTimeoutMillis)
    }
}
