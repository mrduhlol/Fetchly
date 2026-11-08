package com.fetchly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fetchly.app.R
import com.fetchly.app.domain.model.Platform

/** Transparent brand marks; monochrome marks follow the palette for contrast. */
@Composable
fun PlatformIcon(platform: Platform, modifier: Modifier = Modifier, compact: Boolean = false, music: Boolean = false, large: Boolean = false) {
    val brand = when (platform) {
        Platform.YOUTUBE -> (if (music) R.drawable.ic_platform_youtube_music else R.drawable.ic_platform_youtube) to Color(0xFFFF0033)
        Platform.INSTAGRAM -> R.drawable.ic_platform_instagram to Color(0xFFC13584)
        Platform.TIKTOK -> R.drawable.ic_platform_tiktok to Color(0xFF161823)
        Platform.TWITTER -> R.drawable.ic_platform_x to Color(0xFF111111)
        Platform.REDDIT -> R.drawable.ic_platform_reddit to Color(0xFFFF4500)
        Platform.FACEBOOK -> R.drawable.ic_platform_facebook to Color(0xFF0866FF)
        Platform.PINTEREST -> R.drawable.ic_platform_pinterest to Color(0xFFE60023)
        Platform.LINKEDIN -> R.drawable.ic_platform_linkedin to Color(0xFF0A66C2)
        else -> null
    }
    if (brand == null) {
        Icon(Icons.Filled.Info, contentDescription = null, modifier = modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val tint = when (platform) {
            Platform.TIKTOK, Platform.TWITTER -> MaterialTheme.colorScheme.onSurface
            else -> brand.second
        }
        Icon(painterResource(brand.first), contentDescription = null,
            tint = tint, modifier = modifier.size(if (large) 40.dp else if (compact) 24.dp else 28.dp))
    }
}
