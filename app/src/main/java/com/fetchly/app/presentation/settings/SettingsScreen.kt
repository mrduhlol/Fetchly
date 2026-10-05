package com.fetchly.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.data.prefs.DefaultQuality
import com.fetchly.app.data.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val theme by vm.theme.collectAsState()
    val quality by vm.defaultQuality.collectAsState()
    val apiBase by vm.apiBaseUrl.collectAsState()
    var showClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Picker(
                label = "Theme",
                options = ThemeMode.entries.map { it.name },
                selected = theme.name,
                onPick = { vm.setTheme(ThemeMode.valueOf(it)) },
            )
            Spacer(Modifier.height(16.dp))
            Text("Downloads", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Picker(
                label = "Default quality",
                options = listOf("BEST", "ASK"),
                selected = quality.name,
                onPick = { vm.setQuality(DefaultQuality.valueOf(it)) },
            )
            Spacer(Modifier.height(8.dp))
            var draft by remember(apiBase) { mutableStateOf(apiBase) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("API base URL (optional override)") },
                singleLine = true,
            )
            TextButton(onClick = { vm.setApiBase(draft) }) { Text("Save API URL") }
            Spacer(Modifier.height(16.dp))
            Text("History", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { showClear = true }) { Text("Clear history") }
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onAbout) { Text("About Fetchly") }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } },
            title = { Text("Clear history?") },
            text = { Text("This removes history entries. Downloaded files stay on your device.") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { onPick(it); expanded = false })
            }
        }
    }
}
