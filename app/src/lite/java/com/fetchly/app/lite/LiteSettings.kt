package com.fetchly.app.lite

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.BuildConfig
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.ui.components.ExternalLinks
import com.fetchly.app.ui.settings.SettingsViewModel

@Composable
fun LiteSettings(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var notifications by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var systemError by remember { mutableStateOf<String?>(null) }
    val quality by vm.defaultQuality.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val historyMessage by vm.historyMessage.collectAsStateWithLifecycle()
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ParchmentPanel(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (notifications) "On" else "Off", Modifier.weight(1f))
                Switch(notifications, onCheckedChange = {
                    systemError = runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        null
                    }.getOrElse { "Couldn't open notification settings. Open Fetchly Lite in Android Settings." }
                })
            }
            Text("Change notification access in Android's notification settings. Android may still list active background transfers in its task manager.", style = MaterialTheme.typography.bodySmall)
            systemError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("Downloads", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                DefaultQuality.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().selectable(quality == choice, role = Role.RadioButton, onClick = { vm.setDefaultQuality(choice) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(quality == choice, null)
                        Text(if (choice == DefaultQuality.ASK) "Ask every time" else "Best available", Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("Video streams merge and MP3 converts on this phone. Downloads run one at a time.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
                    .onFailure { systemError = "Couldn't open Android app settings." }
            }) { Text("Android download settings") }
            HorizontalDivider()
            Text("Download engine", style = MaterialTheme.typography.titleMedium)
            Text(engine.message)
            if (engine.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { vm.maintainEngine("check") }, enabled = !engine.busy) { Text("Check for engine updates") }
            if (engine.available) TextButton(onClick = { vm.maintainEngine("install") }, enabled = !engine.busy) { Text("Install engine update") }
            if (engine.hasOverride) TextButton(onClick = { vm.maintainEngine("reset") }, enabled = !engine.busy) { Text("Use bundled engine") }
            if (engine.restart) Text("After downloads finish, force stop and reopen Lite to apply the engine change.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("History", style = MaterialTheme.typography.titleMedium)
            historyMessage?.let { Text(it) }
            TextButton(onClick = { confirmClear = true }) { Text("Clear finished history") }
            Text("Saved files remain on your phone; clearing history removes them from this app's Library list.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { if (!ExternalLinks.open(context, ExternalLinks.APP_RELEASES)) systemError = "No browser could open the releases page." }) { Text("Fetchly releases") }
            TextButton(onClick = { about = true }) { Text("About Fetchly Lite") }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear finished history?") },
        text = { Text("Finished entries will be removed, including Library entries. Active downloads and saved files stay.") },
        confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Fetchly Lite") },
        text = { Text("Version ${BuildConfig.VERSION_NAME}\n\nPaste. Choose. Fetch.\nPublic media resolves, downloads and converts on your phone. No account or backend. Source restrictions may prevent downloads.\n\n© mrduhlol\nAGPL-3.0 · yt-dlp (Unlicense), Chaquopy and FFmpegKit (LGPL-3.0).") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } })
}
