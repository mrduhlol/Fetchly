package com.fetchly.app.ui.settings

import com.fetchly.app.ui.components.ExternalLinks
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.ThemeChoice
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.SectionHeader

/**
 * Appearance, download default, history, and a door to About.
 *
 * By design there is nothing to configure about where Fetchly runs: it
 * resolves and downloads entirely on this device.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val historyMessage by viewModel.historyMessage.collectAsStateWithLifecycle()
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val quality by viewModel.defaultQuality.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
        ScreenTopBar("Settings", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

        SectionHeader("Appearance")
        Column(Modifier.selectableGroup()) {
        ThemeChoice.entries.forEach { choice ->
                        PaletteRow(choice, selected = choice == theme, onClick = { viewModel.setTheme(choice) })
        }
        }

        SectionHeader("Downloads")
        Column(Modifier.selectableGroup()) {
        DefaultQuality.entries.forEach { choice ->
            ChoiceRow(
                label = qualityLabel(choice),
                selected = choice == quality,
                onClick = { viewModel.setDefaultQuality(choice) },
            )
        }
        }
        Note("Choose from the formats the source provides. Separate video and audio streams are merged on this device.")

        ActionRow(
            title = "Android download settings",
            subtitle = "Keep notifications allowed and review battery restrictions if background downloads stop.",
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))) }
            },
        )

        SectionHeader("Download engine")
        Note(engine.message)
        Note("Updates use the official yt-dlp package. Restart Fetchly to apply an installed update. Current downloads continue with the existing version.")
        TextButton(onClick = { viewModel.maintainEngine("check") }, enabled = !engine.busy,
            modifier = Modifier.padding(horizontal = 16.dp)) { Text("Check for engine updates") }
        if (engine.restart) {
            Note("After all downloads finish, use Force stop in Android app settings, then reopen Fetchly.")
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))) }
            }) { Text("Open Android app settings") }
        }
        if (engine.available) {
            TextButton(onClick = { viewModel.maintainEngine("install") }, enabled = !engine.busy,
                modifier = Modifier.padding(horizontal = 16.dp)) { Text("Install engine update") }
        }
        if (engine.hasOverride) {
            TextButton(onClick = { viewModel.maintainEngine("reset") }, enabled = !engine.busy,
                modifier = Modifier.padding(horizontal = 16.dp)) { Text("Use bundled engine") }
        }

        ActionRow(
            title = "Fetchly releases",
            subtitle = "See the latest app version and release notes.",
            onClick = { ExternalLinks.open(context, ExternalLinks.APP_RELEASES) },
        )

        SectionHeader("History")
        historyMessage?.let { Note(it) }
        ActionRow(
            title = "Clear finished history",
            subtitle = "Removes finished entries. Active downloads and saved files stay.",
            onClick = { confirmClear = true },
        )

        SectionHeader("About")
        ActionRow(
            title = "About Fetchly",
            subtitle = "Version, engine, limitations and licences",
            onClick = onOpenAbout,
        )
        Spacer(Modifier.padding(bottom = 16.dp))
    }

    }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear finished history?") },
            text = { Text("Finished entries are removed. Active downloads and files already saved stay.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClear = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PaletteRow(choice: ThemeChoice, selected: Boolean, onClick: () -> Unit) {
    val dark = choice == ThemeChoice.DARK
    val previewBackground = if (dark) com.fetchly.app.ui.theme.DarkBackground else com.fetchly.app.ui.theme.LightBackground
    val previewText = if (dark) com.fetchly.app.ui.theme.DarkOnBackground else com.fetchly.app.ui.theme.LightOnBackground
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = previewBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.width(72.dp).padding(10.dp)) {
                    Text("Aa", color = previewText, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        listOf(previewText, com.fetchly.app.ui.theme.DarkSecondary, com.fetchly.app.ui.theme.DarkOutline).forEach {
                            Surface(color = it, shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier.width(12.dp).then(Modifier.height(12.dp))) { }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(themeLabel(choice), style = MaterialTheme.typography.titleMedium)
                Text(when (choice) {
                    ThemeChoice.SYSTEM -> "Match your device appearance"
                    ThemeChoice.DARK -> "Obsidian, ivory & antique gold"
                    ThemeChoice.LIGHT -> "Warm ivory & muted bronze"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private fun themeLabel(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.SYSTEM -> "Follow system"
    ThemeChoice.LIGHT -> "White (Light)"
    ThemeChoice.DARK -> "Gold (Dark)"
}

private fun qualityLabel(choice: DefaultQuality): String = when (choice) {
    DefaultQuality.ASK -> "Ask every time"
    DefaultQuality.BEST -> "Best available"
}
