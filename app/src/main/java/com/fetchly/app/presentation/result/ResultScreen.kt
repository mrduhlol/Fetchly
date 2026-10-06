package com.fetchly.app.presentation.result

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fetchly.app.domain.util.MimeTypes
import com.fetchly.app.presentation.components.FetchButton
import com.fetchly.app.presentation.components.FormatSelector
import com.fetchly.app.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onDownloadStarted: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val info = ui.info
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fetchly") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (info == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Nothing to show. Go back and paste a link.")
                Spacer(Modifier.height(12.dp))
                FetchButton(label = "BACK TO HOME", onClick = onBack)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
            if (info.thumbnailUrl != null) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = "Thumbnail for ${info.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Honest placeholder: no fake preview when the source has none.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = com.fetchly.app.R.drawable.fetchly_logo
                    ),
                    contentDescription = "No preview available",
                    modifier = Modifier
                        .fillMaxSize(0.4f)
                        .align(Alignment.Center),
                )
            }
                info.durationSecs?.let { secs ->
                    Text(
                        "%d:%02d".format(secs / 60, secs % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SuggestionChip(onClick = {}, label = { Text(info.platform.label) })
            Spacer(Modifier.height(4.dp))
            Text(info.title, style = MaterialTheme.typography.titleLarge)
            info.author?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(info.mediaType.name)
                    info.durationSecs?.let { append(" • ${it / 60}:${"%02d".format(it % 60)}") }
                    vm.selectedFormat()?.sizeBytes?.let {
                        append(" • about ${MimeTypes.displaySize(it)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            // Type-specific facts, only when the resolver provided them.
            val selected = vm.selectedFormat()
            val extras = buildList {
                selected?.dimensionsLabel?.let { add(it) }
                selected?.bitrateLabel?.let { add(it) }
                selected?.hasAudio?.let { add(if (it) "Includes audio" else "No audio track") }
            }
            if (extras.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    extras.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (info.videoFormats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("VIDEO", style = MaterialTheme.typography.labelMedium)
                FormatSelector(
                    formats = info.videoFormats,
                    selectedId = ui.selectedFormatId,
                    onSelect = vm::selectFormat,
                )
            }
            if (info.audioFormats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("AUDIO", style = MaterialTheme.typography.labelMedium)
                FormatSelector(
                    formats = info.audioFormats,
                    selectedId = ui.selectedFormatId,
                    onSelect = vm::selectFormat,
                )
            }
            Spacer(Modifier.height(20.dp))
            FetchButton(
                label = "DOWNLOAD",
                onClick = vm::downloadSelected,
                enabled = vm.selectedFormat() != null,
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(ui.state) {
        if (ui.state == com.fetchly.app.domain.model.FetchState.COMPLETED) {
            vm.consumeCompleted()
            onDownloadStarted()
        }
    }

    ui.duplicateOf?.let { existing ->
        AlertDialog(
            onDismissRequest = vm::dismissDuplicate,
            title = { Text("Already downloaded") },
            text = {
                Text("This media in ${existing.quality} ${existing.container.uppercase()} is already on your device.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    existing.localUri?.let { uri ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                uri.toUri(),
                                MimeTypes.resolve(context, uri, existing.container),
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
                    }
                    vm.dismissDuplicate()
                }) { Text("Open existing") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.confirmDuplicateDownload()
                    onDownloadStarted()
                }) { Text("Download again") }
            },
        )
    }

    ui.storageError?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissStorageError,
            title = { Text("Not enough storage") },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = vm::dismissStorageError) { Text("OK") }
            },
        )
    }
}
