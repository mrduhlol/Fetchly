package com.fetchly.app.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.painterResource
import com.fetchly.app.R
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.fetchly.app.data.download.DownloadStages
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.util.FormatSize
import com.fetchly.app.domain.util.RelativeTime
import com.fetchly.app.ui.components.EmptyState
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.SectionHeader

/**
 * Active downloads up top (real stage, real percent, cancel), finished ones
 * below (result, retry, delete). Tapping a finished row opens its details.
 */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, onBack: () -> Unit, initialNotice: String? = null,
    onOpenSettings: () -> Unit = {}, onChooseFormat: (String) -> Unit = {},
    libraryOnly: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var filterName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("ALL") }
    val history = if (libraryOnly) state.history.filter { it.status == DownloadStatus.COMPLETED } else state.history
    val library = DownloadLibrary.filter(history, query, LibraryFilter.valueOf(filterName))
    val context = LocalContext.current
    var detailsFor by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleteFor by remember { mutableStateOf<HistoryEntry?>(null) }

    val busy by viewModel.busyIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { initialNotice?.let { snackbar.showSnackbar(it) } }
    var missing by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    fun openFile(entry: HistoryEntry, share: Boolean = false) {
        scope.launch {
            val result = if (share) EntryFiles.share(context, entry) else EntryFiles.open(context, entry)
            if (result == EntryFiles.Result.MISSING) missing = missing + entry.id
            result.message?.let { snackbar.showSnackbar(it) }
        }
    }
    LaunchedEffect(detailsFor?.id) {
        detailsFor?.takeIf { it.status == DownloadStatus.COMPLETED }?.let {
            if (!EntryFiles.available(context, it)) missing = missing + it.id
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter)) {
        ScreenTopBar(if (libraryOnly) "Library" else "Downloads", onBack = onBack)

        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(if (libraryOnly) "Loading library…" else "Loading downloads…",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }
        if (if (libraryOnly) history.isEmpty() else state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = if (libraryOnly) "Your library starts here" else "No downloads yet",
                subtitle = if (libraryOnly) "Saved videos, audio and images appear here after a download completes." else "Download a link to see its progress and saved file here.",
                modifier = Modifier.weight(1f),
            )
            if (libraryOnly) Image(painterResource(R.drawable.fetchly_library), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
            else com.fetchly.app.ui.components.TempleArtwork(Modifier.fillMaxWidth().height(140.dp))
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (!libraryOnly && state.active.isNotEmpty()) {
                item { SectionHeader("Downloading (${state.active.size})", horizontalPadding = 0.dp) }
                items(state.active, key = { it.entry.id }) { active ->
                    ActiveCard(active = active, enabled = active.entry.id !in busy, onCancel = { viewModel.cancel(active.entry.id) }, onPause = { if (active.entry.status == DownloadStatus.PAUSED) viewModel.retry(active.entry.id) else viewModel.pause(active.entry.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (history.isNotEmpty()) {
                item {
                    if (libraryOnly) {
                        LibraryCategories(history, LibraryFilter.valueOf(filterName)) { filterName = it.name }
                    } else SectionHeader("Finished (${history.size})", horizontalPadding = 0.dp)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(query, { query = it }, label = { Text("Search downloads") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        items(LibraryFilter.entries.filter { !libraryOnly || it != LibraryFilter.FAILED }) { filter ->
                            androidx.compose.material3.FilterChip(selected = filterName == filter.name,
                                onClick = { filterName = filter.name }, label = { Text(filter.label) })
                        }
                    }
                    if (library.isEmpty()) {
                        Text("No downloads match your search or filters.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = { query = ""; filterName = "ALL" }) { Text("Clear filters") }
                    }
                }
                items(library, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpenDetails = { detailsFor = entry },
                        onRetry = { viewModel.retry(entry.id) },
                        onOpen = { openFile(entry) },
                        enabled = entry.id !in busy,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    detailsFor?.let { entry ->
        MediaDetailsSheet(
            entry = entry,
            onDismiss = { detailsFor = null },
            onOpen = { openFile(entry) },
            onShare = { openFile(entry, share = true) },
            onRetry = {
                viewModel.retry(entry.id)
                detailsFor = null
            },
            onDelete = {
                deleteFor = entry
                detailsFor = null
            },
            fileAvailable = entry.outputUri != null && entry.id !in missing,
            onOpenSettings = { detailsFor = null; onOpenSettings() },
            onChooseFormat = { detailsFor = null; onChooseFormat(entry.sourceUrl) },
        )
    }

    deleteFor?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete this download?") },
            text = { Text("The file is also removed from your media library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entry.id, deleteFile = true)
                        deleteFor = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Reuses the existing history filters; counts come exclusively from stored entries. */
@Composable
private fun LibraryCategories(history: List<HistoryEntry>, selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val categories = listOf(LibraryFilter.VIDEO, LibraryFilter.AUDIO, LibraryFilter.IMAGES, LibraryFilter.ALL)
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                pair.forEach { category ->
                    val count = DownloadLibrary.filter(history, "", category).size
                    Surface(
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected == category) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(when (category) {
                                LibraryFilter.VIDEO -> R.drawable.ic_library_video
                                LibraryFilter.AUDIO -> R.drawable.ic_library_audio
                                LibraryFilter.IMAGES -> R.drawable.ic_library_image
                                else -> R.drawable.ic_library_folder
                            }), null, modifier = Modifier.padding(bottom = 8.dp))
                            Text(if (category == LibraryFilter.ALL) "All files" else category.label, style = MaterialTheme.typography.titleMedium)
                            Text("$count ${if (count == 1) "entry" else "entries"}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCard(active: ActiveDownload, enabled: Boolean, onCancel: () -> Unit, onPause: () -> Unit) {
    val paused = active.entry.status == DownloadStatus.PAUSED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).alpha(if (paused) 0.45f else 1f)) {
                    Text(
                        active.entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        active.stageText + speedSuffix(active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPause, enabled = enabled) {
                    Icon(painterResource(if (paused) R.drawable.ic_play_outline else R.drawable.ic_pause_outline),
                        contentDescription = if (paused) "Resume download" else "Pause download",
                        tint = if (paused) androidx.compose.ui.graphics.Color(0xFF239B56) else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onCancel, enabled = enabled && !paused) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel this download",
                        modifier = Modifier.alpha(if (paused) 0.35f else 1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            val percent = active.percent
            if (percent != null) {
                Text("$percent% transferred · ${FormatSize.format(active.bytes).orEmpty()} of ${FormatSize.format(active.total).orEmpty()}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(if (paused) 0.45f else 1f))
                Spacer(Modifier.height(6.dp))
            }
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().alpha(if (paused) 0.4f else 1f),
                    color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpenDetails: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.thumbnailUrl != null) {
                coil.compose.AsyncImage(
                    model = entry.thumbnailUrl, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
                )
            } else Icon(statusIcon(entry.status), contentDescription = null, tint = statusTint(entry.status))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitleFor(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.CANCELLED || entry.status == DownloadStatus.PAUSED) {
                TextButton(onClick = onRetry, enabled = enabled) { Text(if (entry.status == DownloadStatus.PAUSED) "Resume" else "Retry") }
            } else if (entry.status == DownloadStatus.COMPLETED) {
                TextButton(onClick = onOpen) { Text("Open file") }
            }
        }
    }
}

private fun statusIcon(status: DownloadStatus): ImageVector = when (status) {
    DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle
    DownloadStatus.FAILED -> Icons.Filled.Warning
    DownloadStatus.CANCELLED -> Icons.Filled.Close
    else -> Icons.Filled.Info
}

@Composable
private fun statusTint(status: DownloadStatus) = when (status) {
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun subtitleFor(entry: HistoryEntry): String {
    val whenText = RelativeTime.format(entry.updatedAt)
    return when (entry.status) {
        DownloadStatus.COMPLETED -> listOfNotNull(
            FormatSize.format(entry.sizeBytes)?.let { "Saved · $it" } ?: "Saved",
            whenText,
        ).joinToString(" · ")

        DownloadStatus.FAILED -> listOfNotNull(
            entry.errorMessage ?: "The download failed.",
            whenText,
        ).joinToString(" · ")

        DownloadStatus.CANCELLED -> "Cancelled · $whenText"
        DownloadStatus.PAUSED -> "Paused · $whenText"

        else -> whenText
    }
}

private fun speedSuffix(active: ActiveDownload): String {
    val speed = if (DownloadStages.isTransfer(active.stageText)) active.work?.speedBytesPerSecond ?: 0L else 0L
    return if (speed > 0L) " · ${FormatSize.formatApprox(speed).orEmpty()}/s" else ""
}
