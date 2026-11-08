package com.fetchly.app.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.unit.dp
import com.fetchly.app.ui.components.PlatformIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupportedApps(enabled: Boolean, selected: SupportedApp?, onSelect: (SupportedApp) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Choose an app", style = MaterialTheme.typography.titleMedium)
        // Three columns leave room for full platform names and the Music shortcut.
        SupportedApp.entries.chunked(3).forEach { shortcuts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { shortcut ->
                    val name = shortcut.label
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).alpha(if (enabled) 1f else 0.38f)
                            .selectable(selected = shortcut == selected, enabled = enabled, role = Role.RadioButton) { onSelect(shortcut) }
                            .semantics { contentDescription = name },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(36.dp)
                            .border(BorderStroke(0.5.dp, if (shortcut == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { PlatformIcon(shortcut.platform, compact = true, music = shortcut == SupportedApp.YOUTUBE_MUSIC) }
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
    }
}
