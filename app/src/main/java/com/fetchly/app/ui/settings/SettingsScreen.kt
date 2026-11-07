package com.fetchly.app.ui.settings

import androidx.compose.material.icons.filled.Settings
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
    onOpenAppearance: () -> Unit = {},
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

                ActionRow("Appearance", themeLabel(theme), onOpenAppearance)

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
fun AppearanceScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenTopBar("Appearance", onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Make it yours", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                Note("Choose a palette. Your selection applies throughout Fetchly and stays after you close the app.")
                SectionHeader("Appearance")
                Column(Modifier.selectableGroup()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        listOf(ThemeChoice.DARK, ThemeChoice.LIGHT).forEach { choice ->
                            PaletteTile(choice, choice == theme, { viewModel.setTheme(choice) }, Modifier.weight(1f))
                        }
                    }
                    ChoiceRow("Follow system", theme == ThemeChoice.SYSTEM) { viewModel.setTheme(ThemeChoice.SYSTEM) }
                }

                Spacer(Modifier.height(20.dp))
                Note("Simple ivory actions, clear type, and a little Apollo. Both palettes share the same controls and layout.")
                com.fetchly.app.ui.components.TempleArtwork(Modifier.fillMaxWidth().height(180.dp))
            }
        }
    }
}

@Composable
private fun PaletteTile(choice: ThemeChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = choice == ThemeChoice.DARK
    val background = if (dark) com.fetchly.app.ui.theme.DarkBackground else com.fetchly.app.ui.theme.LightBackground
    val foreground = if (dark) com.fetchly.app.ui.theme.DarkOnBackground else com.fetchly.app.ui.theme.LightOnBackground
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.fetchly.app.R.drawable.fetchly_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(104.dp),
                colorFilter = if (dark) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }),
            )
            Spacer(Modifier.height(8.dp))
            Text(themeLabel(choice), style = MaterialTheme.typography.labelLarge, color = foreground)
            Text(if (selected) "Selected" else "Tap to apply", style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.75f))
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
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(when (title) {
            "Appearance" -> com.fetchly.app.R.drawable.ic_appearance
            "Android download settings" -> com.fetchly.app.R.drawable.ic_stat_download
            "Fetchly releases" -> com.fetchly.app.R.drawable.ic_external
            "Clear finished history" -> com.fetchly.app.R.drawable.ic_clock
            else -> com.fetchly.app.R.drawable.ic_info
        }), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.fetchly.app.R.drawable.ic_chevron), null)
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
