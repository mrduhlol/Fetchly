package com.fetchly.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.ui.components.PlatformIcon
import com.fetchly.app.ui.components.ExternalLinks

private data class PlatformShortcut(val platform: Platform, val url: String)

private val platformShortcuts = listOf(
    PlatformShortcut(Platform.YOUTUBE, "https://www.youtube.com"),
    PlatformShortcut(Platform.INSTAGRAM, "https://www.instagram.com"),
    PlatformShortcut(Platform.TIKTOK, "https://www.tiktok.com"),
    PlatformShortcut(Platform.TWITTER, "https://x.com"),
    PlatformShortcut(Platform.REDDIT, "https://www.reddit.com"),
    PlatformShortcut(Platform.FACEBOOK, "https://www.facebook.com"),
    PlatformShortcut(Platform.PINTEREST, "https://www.pinterest.com"),
    PlatformShortcut(Platform.LINKEDIN, "https://www.linkedin.com"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupportedApps(enabled: Boolean) {
    val context = LocalContext.current
    var failedPlatform by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Apps we support", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap an app to find a link.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            platformShortcuts.forEach { shortcut ->
                val name = if (shortcut.platform == Platform.TWITTER) "X" else PlatformDetector.label(shortcut.platform)
                OutlinedButton(
                    onClick = {
                        failedPlatform = if (ExternalLinks.open(context, shortcut.url)) null else name
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    PlatformIcon(shortcut.platform)
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            "YouTube works best. Other apps depend on the link. You can also paste a direct file link.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        failedPlatform?.let {
            Text("Couldn't open $it. Open it on your device and copy a link.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
