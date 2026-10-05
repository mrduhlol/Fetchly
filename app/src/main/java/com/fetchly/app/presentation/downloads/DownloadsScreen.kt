package com.fetchly.app.presentation.downloads

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.util.DayBucket
import com.fetchly.app.domain.util.MimeTypes
import com.fetchly.app.domain.util.RelativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    vm: DownloadsViewModel = viewModel(),
) {
    val rows by vm.rows.collectAsState()
    val notice by vm.notice.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snacks.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    var selected by remember { mutableStateOf<DownloadRowState?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var showDetails by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleteResult by remember { mutableStateOf<String?>(null) }

    fun openMedia(entry: HistoryEntry) {
        entry.localUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri.toUri(), MimeTypes.resolve(context, uri, entry.container))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
        }
    }

    fun shareMedia(entry: HistoryEntry) {
        val intent = if (entry.localUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = MimeTypes.resolve(context, entry.localUri, entry.container)
                putExtra(Intent.EXTRA_STREAM, entry.localUri.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entry.sourceUrl)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste a link above and your downloads will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onHome) { Text("Paste a link") }
            }
            return@Scaffold
        }
        val now = System.currentTimeMillis()
        val grouped = rows.groupBy { RelativeTime.bucket(now, it.entry.createdAt) }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            listOf(
                DayBucket.TODAY to "Today",
                DayBucket.YESTERDAY to "Yesterday",
                DayBucket.EARLIER to "Earlier",
            ).forEach { (bucket, label) ->
                val bucketRows = grouped[bucket].orEmpty()
                if (bucketRows.isNotEmpty()) {
                    item(key = "header-$bucket") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(bucketRows, key = { it.entry.id }) { row ->
                        DownloadRow(
                            row = row,
                            onTap = {
                                selected = row
                                if (!row.isActive) showDetails = row.entry
                            },
                            onOpen = { openMedia(row.entry) },
                            onShare = { shareMedia(row.entry) },
                            onRetry = { vm.retry(row.entry) },
                            onCancel = { vm.cancel(row.entry) },
                            onDelete = { pendingDelete = row.entry },
                        )
                    }
                }
            }
        }
    }

    // Active download detail: live progress + cancel.
    selected?.let { row ->
        if (row.isActive) {
            ActiveDownloadSheet(
                row = row,
                onDismiss = { selected = null },
                onCancel = { vm.cancel(row.entry); selected = null },
            )
        } else {
            selected = null
        }
    }

    showDetails?.let { entry ->
        MediaDetailsSheet(entry = entry, onDismiss = { showDetails = null })
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${entry.title}\"?") },
            text = { Text("Removing it from history keeps the file on your device. Deleting the file removes it permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.removeFromHistory(entry)
                }) { Text("Remove from history") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = vm.deleteFile(entry)
                        deleteResult = if (ok) "File deleted." else "Couldn't delete the file."
                    }
                }) { Text("Delete file") }
            },
        )
    }

    deleteResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteResult = null },
            confirmButton = { TextButton(onClick = { deleteResult = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowState,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = row.entry
    val pct = row.progress?.let { p ->
        if (p.total > 0) (p.done * 100 / p.total).toInt().coerceIn(0, 100) else null
    }
    val statusText = when (entry.status) {
        DownloadStatus.QUEUED -> if (pct != null) "Queued • $pct%" else "Queued • Waiting…"
        DownloadStatus.DOWNLOADING -> if (pct != null) "Downloading • $pct%" else "Downloading…"
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .semantics { contentDescription = "${entry.title}, $statusText" },
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onTap).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = "Thumbnail for ${entry.title}",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(
                        "${entry.quality} • ${entry.container.uppercase()} • $statusText",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        RelativeTime.timeAgo(System.currentTimeMillis(), entry.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.isActive && pct != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row {
                if (entry.status == DownloadStatus.COMPLETED && entry.localUri != null) {
                    TextButton(onClick = onOpen) { Text("Open") }
                    TextButton(onClick = onShare) { Text("Share") }
                }
                if (entry.status == DownloadStatus.FAILED) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                if (row.isActive) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveDownloadSheet(
    row: DownloadRowState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    val entry = row.entry
    val p = row.progress
    val pct = p?.let { if (it.total > 0) (it.done * 100 / it.total).toInt().coerceIn(0, 100) else null }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.quality} • ${entry.container.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (pct != null) {
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "$pct% • ${MimeTypes.displaySize(p?.done)} / ${MimeTypes.displaySize(p?.total)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if ((p?.speedBps ?: 0.0) > 0) {
                    Text(
                        "${MimeTypes.displaySize(p!!.speedBps.toLong())}/s",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Queued • Waiting…", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("Cancel download") }
        }
    }
}
