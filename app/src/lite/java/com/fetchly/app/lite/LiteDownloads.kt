package com.fetchly.app.lite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.util.FormatSize
import com.fetchly.app.ui.downloads.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiteDownloads(vm: DownloadsViewModel, library: Boolean, onFetch: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busyIds.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var delete by remember { mutableStateOf<HistoryEntry?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { message = it } }
    val history = state.history.filter { if (library) it.status == DownloadStatus.COMPLETED else it.status != DownloadStatus.COMPLETED }
    val filtered = DownloadLibrary.filter(history, query, LibraryFilter.valueOf(filterName))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ParchmentPanel(Modifier.fillMaxWidth()) {
                Text(if (library) "Library" else "Downloads", style = MaterialTheme.typography.headlineMedium)
                Text(if (library) "The things you chose to keep." else "One at a time. Every byte counts.")
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (history.isNotEmpty()) {
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibraryFilter.entries.filter { it != LibraryFilter.FAILED }) { filter ->
                            FilterChip(filterName == filter.name, { filterName = filter.name }, label = { Text(filter.label) })
                        }
                    }
                }
                if (!state.loading && history.isEmpty() && (library || state.active.isEmpty())) {
                    Text(if (library) "Completed downloads appear here." else "Your downloads will appear here.")
                    TextButton(onClick = onFetch) { Text("Fetch a link") }
                }
            }
        }
        if (!library) items(state.active, key = { it.entry.id }) { active ->
            val entry = active.entry
            ParchmentPanel(Modifier.fillMaxWidth()) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${entry.container.uppercase()} · ${entry.optionLabel}", style = MaterialTheme.typography.bodySmall)
                Text(active.stageText)
                if (active.percent != null) LinearProgressIndicator(progress = { active.percent!! / 100f }, modifier = Modifier.fillMaxWidth())
                else if (entry.status != DownloadStatus.PAUSED) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (active.bytes > 0) Text("${FormatSize.format(active.bytes)} / ${FormatSize.format(active.total.takeIf { it > 0 }) ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (entry.status == DownloadStatus.PAUSED) vm.retry(entry.id) else vm.pause(entry.id) }, enabled = entry.id !in busy) {
                        Text(if (entry.status == DownloadStatus.PAUSED) "Resume" else "Pause")
                    }
                    TextButton(onClick = { vm.cancel(entry.id) }, enabled = entry.id !in busy) { Text("Cancel") }
                }
            }
        }
        if (history.isNotEmpty() && filtered.isEmpty()) item { ParchmentPanel { Text("No items match your search or filter.") } }
        items(filtered, key = { it.id }) { entry ->
            ParchmentPanel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(entry.thumbnailUrl, null, Modifier.size(64.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${entry.container.uppercase()} · ${entry.optionLabel}", style = MaterialTheme.typography.bodySmall)
                        Text(FormatSize.format(entry.sizeBytes) ?: entry.status.name.lowercase(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                entry.errorMessage?.takeIf { entry.status != DownloadStatus.COMPLETED }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (library) {
                        TextButton(onClick = { scope.launch { message = EntryFiles.open(context, entry).message } }, enabled = entry.id !in busy) { Text("Open") }
                        TextButton(onClick = { scope.launch { message = EntryFiles.share(context, entry).message } }, enabled = entry.id !in busy) { Text("Share") }
                    } else TextButton(onClick = { vm.retry(entry.id) }, enabled = entry.id !in busy) { Text("Retry") }
                    TextButton(onClick = { delete = entry }, enabled = entry.id !in busy) { Text(if (library) "Delete" else "Remove") }
                }
            }
        }
        item { Spacer(Modifier.height(180.dp)) }
    }
    delete?.let { entry ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text(if (library) "Delete this file?" else "Remove this entry?") },
            text = { Text(if (library) "The saved file and its library entry will be deleted." else "The entry will be removed from Downloads.") },
            confirmButton = { TextButton(onClick = { vm.delete(entry.id, library); delete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}
