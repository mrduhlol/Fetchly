package com.fetchly.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.ui.components.EmptyState
import com.fetchly.app.ui.components.QualityRow
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.StatusCard
import com.fetchly.app.domain.util.FormatSize

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onBack: () -> Unit,
    onQueued: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.queued) {
        if (state.queued) {
            viewModel.consumeQueued()
            onQueued()
        }
    }
    val media = state.media
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenTopBar(title = "Preview & download", onBack = onBack)
            if (media == null) {
                EmptyState(Icons.Filled.PlayArrow, "No preview yet", "Go back and fetch a link to see its available formats.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Thumbnail(media)
                        Spacer(Modifier.height(20.dp))
                        Text(media.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(factsFor(media), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        Text("Make it yours", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Choose a quality and file format.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (media.options.isEmpty()) {
                        item { Text("No downloadable formats were returned for this link.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    media.options.groupBy { it.kind }.forEach { (kind, options) ->
                        item {
                            Text(
                                when (kind) {
                                    QualityKind.VIDEO -> "Video"
                                    QualityKind.AUDIO -> "Audio only"
                                    QualityKind.FILE -> "File"
                                },
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(options, key = { it.id }) { option ->
                            QualityRow(option, option.id == state.selectedId, { if (!state.enqueuing) viewModel.select(option.id) })
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.error?.let { StatusCard(it, isError = true) }
                        Text(
                            state.selected?.let { "${it.label} · ${it.container.uppercase()} · ${FormatSize.formatApprox(it.sizeBytes) ?: "Size unknown"}" }
                                ?: "Select a format to continue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::download,
                            enabled = state.selected != null && !state.enqueuing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            if (state.enqueuing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(if (state.enqueuing) "Adding to downloads…" else "Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(media: MediaInfo) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (media.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = media.thumbnailUrl,
                contentDescription = "Preview of ${media.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                },
                error = { ThumbnailFallback() },
            )
        } else {
            ThumbnailFallback()
        }
    }
}

@Composable
private fun ThumbnailFallback() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.PlayArrow, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Preview unavailable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun factsFor(media: MediaInfo): String = buildList {
    media.uploader?.let(::add)
    media.durationSeconds?.let { add(formatDuration(it)) }
    add(PlatformDetector.label(media.platform))
}.joinToString(" · ")

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
