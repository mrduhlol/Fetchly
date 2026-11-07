package com.fetchly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** Brand marks share a neutral background so they work in both themes. */
@Composable
fun PlatformIcon(platform: Platform, modifier: Modifier = Modifier) {
    val brand = when (platform) {
        Platform.YOUTUBE -> R.drawable.ic_platform_youtube to Color(0xFFFF0033)
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
        Box(modifier.size(28.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(brand.first), contentDescription = null,
                tint = brand.second, modifier = Modifier.size(18.dp))
        }
    }
}
