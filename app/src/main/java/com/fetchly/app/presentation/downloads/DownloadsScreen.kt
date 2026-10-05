package com.fetchly.app.presentation.downloads

import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fetchly.app.domain.model.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    vm: DownloadsViewModel = viewModel(),
) {
    val items by vm.history.collectAsState()
    val context = LocalContext.current

    Scaffold(
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
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Text("No downloads yet.")
                Text("Paste a link on the home screen to start.", style = MaterialTheme.typography.bodySmall)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            items(items, key = { it.id }) { entry ->
                DownloadRow(
                    entry = entry,
                    onOpen = {
                        entry.localUri?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = uri.toUri()
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
                        }
                    },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, entry.localUri ?: entry.sourceUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    },
                    onDelete = { vm.delete(entry) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    "${entry.quality} • ${entry.container.uppercase()} • ${entry.status.name.lowercase()
                        .replaceFirstChar { it.titlecase() }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (entry.localUri != null) {
                        TextButton(onClick = onOpen) { Text("Open") }
                        TextButton(onClick = onShare) { Text("Share") }
                    }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}
