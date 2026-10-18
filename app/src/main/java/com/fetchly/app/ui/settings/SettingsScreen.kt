package com.fetchly.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val quality by viewModel.defaultQuality.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar("Settings", onBack = onBack)

        SectionHeader("Appearance")
        ThemeChoice.entries.forEach { choice ->
            ChoiceRow(
                label = themeLabel(choice),
                selected = choice == theme,
                onClick = { viewModel.setTheme(choice) },
            )
        }

        SectionHeader("Downloads")
        DefaultQuality.entries.forEach { choice ->
            ChoiceRow(
                label = qualityLabel(choice),
                selected = choice == quality,
                onClick = { viewModel.setDefaultQuality(choice) },
            )
        }
        Note("Fetchly always fetches the best quality available and merges video + audio on this device.")

        SectionHeader("History")
        ActionRow(
            title = "Clear history",
            subtitle = "Empties the download list. Files stay in your media library.",
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("The download list is emptied. Files already saved stay in your media library.") },
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
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

private fun qualityLabel(choice: DefaultQuality): String = when (choice) {
    DefaultQuality.ASK -> "Ask every time"
    DefaultQuality.BEST -> "Best available"
}
