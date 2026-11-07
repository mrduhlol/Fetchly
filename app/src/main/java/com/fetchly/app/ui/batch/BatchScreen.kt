package com.fetchly.app.ui.batch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.domain.batch.BatchQuality
import com.fetchly.app.ui.components.ScreenTopBar

@Composable
fun BatchScreen(viewModel: BatchViewModel, onBack: () -> Unit, onDownloads: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
        ScreenTopBar("Multiple downloads", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add your links", style = MaterialTheme.typography.titleLarge)
                    Text("Paste up to 50 public links, one per line. Two downloads run at a time.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(state.input, viewModel::input, label = { Text("Links, one per line") },
                        enabled = !state.checking && !state.adding, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                    if (state.checking) TextButton(onClick = viewModel::cancel) { Text("Stop checking") }
                    else FilledTonalButton(onClick = viewModel::check, enabled = !state.adding && state.input.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.rows.isEmpty()) "Check links" else "Check links again") }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }, enabled = !state.adding) { Text("Quality: ${state.quality.label}") }
                        DropdownMenu(expanded, { expanded = false }) { BatchQuality.entries.forEach { q ->
                            DropdownMenuItem(text = { Text(q.label) }, onClick = { viewModel.quality(q); expanded = false })
                        } }
                    }
                }
            }
            items(state.rows, key = { it.url }) { row ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Checkbox(row.selected, { viewModel.select(row.url, it) }, enabled = !state.adding && !row.added, modifier = Modifier.semantics { contentDescription = "Select ${row.media?.title ?: row.url}" })
                            Column(Modifier.weight(1f)) {
                                Text(row.media?.title ?: row.url, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                                Text(row.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (row.media == null && !state.checking) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            var failedOpen by remember { mutableStateOf(false) }
                            TextButton(onClick = { failedOpen = !com.fetchly.app.ui.components.ExternalLinks.open(context, row.url) }) { Text("Open source") }
                            if (failedOpen) Text("No app could open this link.")
                        }
                        if (row.media != null && !row.added) {
                            OutlinedTextField(row.name, { viewModel.name(row.url, it) }, label = { Text("File name") },
                                enabled = !state.adding, singleLine = true, modifier = Modifier.fillMaxWidth())
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }, enabled = !state.adding) { Text(row.option?.let { "${it.label} · ${it.container}" } ?: "Choose an available format") }
                                DropdownMenu(expanded, { expanded = false }) {
                                    row.media.options.filter { !it.silentVideo }.forEach { option ->
                                        DropdownMenuItem(text = { Text("${option.label} · ${option.container} · ${option.audioDescription}") },
                                            onClick = { viewModel.option(row.url, option.id); expanded = false })
                                    }
                                }
                            }
                            if (row.option == null) Text("No format matches the default. Choose another format or deselect this link.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val count = state.rows.count { it.selected && !it.added && it.media != null && it.option != null }
            Button(viewModel::download, enabled = count > 0 && !state.checking && !state.adding, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (state.adding) "Adding downloads…" else if (count == 1) "Download 1 file" else "Download $count files")
            }
            TextButton(onDownloads, modifier = Modifier.fillMaxWidth()) { Text("View downloads") }
        }
    }
    }
}
