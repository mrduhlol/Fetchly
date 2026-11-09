package com.fetchly.app.lite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF211C18)
val Parchment = Color(0xFFEFD5AA)
private val Clay = Color(0xFFA75429)

@Composable
fun LiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Parchment, primaryContainer = Parchment, onPrimaryContainer = Ink,
        secondary = Color(0xFF674325), onSecondary = Parchment,
        background = Color.Black, onBackground = Parchment,
        surface = Parchment, onSurface = Ink, surfaceVariant = Color(0xFFE0C398),
        onSurfaceVariant = Color(0xFF493523), outline = Color(0xFF805C37),
        surfaceContainer = Parchment, surfaceContainerLow = Parchment,
        error = Color(0xFF8A2018), onError = Color.White,
    ), shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ), typography = Typography(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
    ), content = content)
}

/** Fit artwork without distortion; a black base makes 77% opacity darken it. */
@Composable
fun LiteBackdrop(art: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Color.Black)) {
        Image(painterResource(art), null, Modifier.fillMaxSize(), alpha = .77f, contentScale = ContentScale.Fit)
        content()
    }
}

@Composable
fun ParchmentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = Parchment, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
