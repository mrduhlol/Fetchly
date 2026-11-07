package com.fetchly.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.fetchly.app.R

/** Fade every edge into the page so the artwork reads as a vignette, never an inset image. */
@Composable
fun TempleArtwork(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.background
    val artwork = if (surface.luminance() < 0.5f) R.drawable.fetchly_temple_dark else R.drawable.fetchly_temple_light
    Box(modifier) {
        Image(painterResource(artwork), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to surface,
            0.02f to surface,
            0.30f to surface.copy(alpha = 0f),
            0.68f to surface.copy(alpha = 0f),
            0.98f to surface,
            1f to surface,
        )))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to surface,
            0.015f to surface,
            0.10f to surface.copy(alpha = 0f),
            0.90f to surface.copy(alpha = 0f),
            0.985f to surface,
            1f to surface,
        )))
    }
}
