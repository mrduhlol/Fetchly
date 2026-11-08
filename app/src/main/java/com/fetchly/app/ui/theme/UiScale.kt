package com.fetchly.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit

enum class UiScale(val percent: Int, internal val factor: Float) {
    COMPACT(80, 0.8f),
    STANDARD(100, 1.0f),
    LARGE(120, 1.2f);

    companion object {
        fun fromPercent(percent: Int?): UiScale = entries.firstOrNull { it.percent == percent } ?: COMPACT
    }
}

internal const val DEFAULT_UI_SCALE = 0.8f

/** Scale app units without replacing Android's accessibility font conversion curve. */
internal class ScaledDensity(
    private val platform: Density,
    scale: Float = DEFAULT_UI_SCALE,
) : Density {
    override val density = platform.density * scale
    override val fontScale: Float get() = platform.fontScale
    override fun Dp.toSp(): TextUnit = with(platform) { this@toSp.toSp() }
    override fun TextUnit.toDp(): Dp = with(platform) { this@toDp.toDp() }
}

/** Visual zoom must not shrink the physical touch target or change gesture thresholds. */
internal class ScaledViewConfiguration(
    platform: ViewConfiguration,
    scale: Float = DEFAULT_UI_SCALE,
) : ViewConfiguration by platform {
    override val minimumTouchTargetSize = DpSize(
        platform.minimumTouchTargetSize.width / scale,
        platform.minimumTouchTargetSize.height / scale,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultUiScale(content: @Composable () -> Unit) {
    ScaledUi( UiScale.COMPACT, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaledUi(scale: UiScale, content: @Composable () -> Unit) {
    val factor = scale.factor
    val platformDensity = LocalDensity.current
    val platformConfiguration = LocalViewConfiguration.current
    val minimumInteractiveSize = LocalMinimumInteractiveComponentSize.current
    val scaledDensity = remember(platformDensity, factor) { ScaledDensity(platformDensity, factor) }
    val scaledConfiguration = remember(platformConfiguration, factor) { ScaledViewConfiguration(platformConfiguration, factor) }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalViewConfiguration provides scaledConfiguration,
        LocalMinimumInteractiveComponentSize provides minimumInteractiveSize / factor,
        content = content,
    )
}
