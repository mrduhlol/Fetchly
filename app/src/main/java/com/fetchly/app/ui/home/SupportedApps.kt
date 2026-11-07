package com.fetchly.app.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Apps we support", style = MaterialTheme.typography.titleMedium)
        // Four equal columns keep all eight shortcuts directly below the link panel.
        platformShortcuts.chunked(4).forEach { shortcuts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { shortcut ->
                    val name = if (shortcut.platform == Platform.TWITTER) "X" else PlatformDetector.label(shortcut.platform)
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).alpha(if (enabled) 1f else 0.38f)
                            .clickable(enabled = enabled, role = Role.Button) {
                                failedPlatform = if (ExternalLinks.open(context, shortcut.url)) null else name
                            }
                            .semantics { contentDescription = name },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(36.dp)
                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { PlatformIcon(shortcut.platform, compact = true) }
                        Text(name, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp).clearAndSetSemantics { })
                    }
                }
            }
        }
        Text(
            "YouTube works best. Other apps vary by link.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        failedPlatform?.let {
            Text("Couldn't open $it. Open it on your device and copy a link.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
