package com.fetchly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.fetchly.app.data.prefs.SettingsStore

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = LightPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF635D54),
    surfaceDim = DarkSurface,
    surfaceBright = Color(0xFF3A3630),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    surfaceContainerLow = Color(0xFF191817),
    surfaceContainer = Color(0xFF201E1B),
    surfaceContainerHigh = Color(0xFF292622),
    surfaceContainerHighest = Color(0xFF34302B),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    inverseSurface = DarkSurfaceVariant,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = DarkSecondary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFD7CEC1),
    surfaceDim = Color(0xFFE2DACF),
    surfaceBright = LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1EAE0),
    surfaceContainer = Color(0xFFECE3D7),
    surfaceContainerHigh = Color(0xFFE6DCCD),
    surfaceContainerHighest = Color(0xFFDFD3C2),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

@Composable
fun FetchlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiScale: UiScale = UiScale.COMPACT,
    content: @Composable () -> Unit,
) {
    ScaledUi(uiScale) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}

@Composable
fun FetchlyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val settings = androidx.compose.runtime.remember(context) { SettingsStore(context) }
    val uiScale by settings.uiScale.collectAsState(initial = UiScale.COMPACT)
    FetchlyTheme(darkTheme = darkTheme, uiScale = uiScale, content = content)
}
