package com.fetchly.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import com.fetchly.app.data.prefs.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentViolet,
    background = NavyBlack,
    surface = NavySurface,
    surfaceVariant = NavyCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val LightScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentViolet,
)

/** Blue → violet brand gradient for CTAs, selected quality, progress. */
val BrandGradient = Brush.horizontalGradient(listOf(AccentBlue, AccentViolet))

@Composable
fun FetchlyTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !useDark -> dynamicLightColorScheme(context)
        useDark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
