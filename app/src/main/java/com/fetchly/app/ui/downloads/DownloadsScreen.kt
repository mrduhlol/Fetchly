package com.fetchly.app.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
fun DownloadsScreen(viewModel: DownloadsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var detailsFor by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleteFor by remember { mutableStateOf<HistoryEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Downloads", onBack = onBack)

        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = "No downloads yet",
                subtitle = "Links you fetch will appear here with their progress.",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (state.active.isNotEmpty()) {
                item { SectionHeader("Active") }
                items(state.active, key = { it.entry.id }) { active ->
                    ActiveCard(active = active, onCancel = { viewModel.cancel(active.entry.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (state.history.isNotEmpty()) {
                item { SectionHeader("History") }
                items(state.history, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpenDetails = { detailsFor = entry },
                        onRetry = { viewModel.retry(entry.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    detailsFor?.let { entry ->
        MediaDetailsSheet(
            entry = entry,
            onDismiss = { detailsFor = null },
            onOpen = { EntryFiles.open(context, entry) },
            onShare = { EntryFiles.share(context, entry) },
            onRetry = {
                viewModel.retry(entry.id)
                detailsFor = null
            },
            onDelete = {
                deleteFor = entry
                detailsFor = null
            },
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

@Composable
private fun ActiveCard(active: ActiveDownload, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        active.entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        active.stageText + speedSuffix(active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel this download")
                }
            }
            Spacer(Modifier.height(10.dp))
            val percent = active.percent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
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
) {
    Surface(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                statusIcon(entry.status),
                contentDescription = null,
                tint = statusTint(entry.status),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
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
            if (entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.CANCELLED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry this download")
                }
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
            FormatSize.format(entry.sizeBytes)?.let { "Saved — $it" } ?: "Saved",
            whenText,
        ).joinToString(" · ")

        DownloadStatus.FAILED -> listOfNotNull(
            entry.errorMessage ?: "The download failed.",
            whenText,
        ).joinToString(" · ")

        DownloadStatus.CANCELLED -> "Cancelled · $whenText"

        else -> whenText
    }
}

private fun speedSuffix(active: ActiveDownload): String {
    val speed = active.work?.speedBytesPerSecond ?: 0L
    return if (speed > 0L) " · ${FormatSize.formatApprox(speed).orEmpty()}/s" else ""
}
