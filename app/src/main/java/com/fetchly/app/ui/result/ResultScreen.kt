package com.fetchly.app.ui.result

import com.fetchly.app.ui.components.PlaqueButton

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
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.ui.components.PlatformIcon
import com.fetchly.app.ui.components.EmptyState
import com.fetchly.app.ui.components.QualityRow
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.StatusCard
import com.fetchly.app.domain.util.FormatSize

@OptIn(ExperimentalLayoutApi::class)
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
    var confirmSilent by remember { mutableStateOf(false) }
    val media = state.media
    val availableVideoFormats = state.visibleOptions.map { it.container.lowercase() }.distinct()
    val defaultVideoFormat = listOf("mp4", "mkv").firstOrNull { it in availableVideoFormats }
    var selectedVideoFormat by remember(media, defaultVideoFormat) { mutableStateOf(defaultVideoFormat) }
    LaunchedEffect(media, state.activeKind, defaultVideoFormat) {
        if (state.activeKind == QualityKind.VIDEO && defaultVideoFormat != null &&
            state.selected?.container?.equals(selectedVideoFormat, ignoreCase = true) != true) {
            state.visibleOptions.firstOrNull { it.container.equals(defaultVideoFormat, ignoreCase = true) }
                ?.let { viewModel.select(it.id) }
        }
    }
    if (confirmSilent) {
        AlertDialog(
            onDismissRequest = { confirmSilent = false },
            title = { Text("Download without sound?") },
            text = { Text("This source offers no audio with the selected video. Choose another quality for sound, if available.") },
            confirmButton = { TextButton(onClick = { confirmSilent = false; viewModel.download() }) { Text("Download silent video") } },
            dismissButton = { TextButton(onClick = { confirmSilent = false }) { Text("Choose another") } },
        )
    }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenTopBar(title = "Fetch options", onBack = onBack)
            if (media == null) {
                EmptyState(Icons.Filled.PlayArrow, "No preview yet", "Go back and fetch a link to see its available formats.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("Fetch media", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Thumbnail(media, Modifier.fillMaxWidth().aspectRatio(
                                if ((media.height ?: 0) > (media.width ?: Int.MAX_VALUE)) 9f / 16f else 16f / 9f))
                            Text(media.title, style = MaterialTheme.typography.titleLarge, maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlatformBadge(media.platform)
                                if (factsFor(media).isNotBlank()) Text(factsFor(media),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Choose a format", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(when (state.activeKind) {
                            QualityKind.VIDEO -> "Higher quality uses more storage."
                            QualityKind.AUDIO -> "Choose an MP3 bitrate."
                            else -> "Save the original file."
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        OutlinedTextField(state.outputTitle, viewModel::rename, label = { Text("File name") },
                            enabled = !state.enqueuing, singleLine = true, modifier = Modifier.fillMaxWidth())
                        state.selected?.let { Text(com.fetchly.app.domain.util.Filenames.displayName(state.outputTitle, it), style = MaterialTheme.typography.bodySmall) }
                    }
                    if (media.options.isEmpty()) {
                        item { Text("No downloadable formats were returned for this link.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (media.options.isNotEmpty()) {
                        item {
                            if (state.activeKind == QualityKind.VIDEO) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    availableVideoFormats.forEach { format ->
                                        FilterChip(
                                            selected = selectedVideoFormat == format,
                                            onClick = {
                                                selectedVideoFormat = format
                                                state.visibleOptions.firstOrNull { it.container.equals(format, true) }
                                                    ?.let { viewModel.select(it.id) }
                                            },
                                            enabled = !state.enqueuing,
                                            label = { Text(format.uppercase()) },
                                        )
                                    }
                                }
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                media.options.map { it.kind }.distinct().forEach { kind ->
                                    FilterChip(
                                        selected = state.activeKind == kind,
                                        onClick = { if (state.activeKind != kind) viewModel.selectKind(kind) },
                                        enabled = !state.enqueuing,
                                        label = {
                                            Text(when (kind) {
                                                QualityKind.VIDEO -> "Video"
                                                QualityKind.AUDIO -> "Audio"
                                                QualityKind.FILE -> "File"
                                            })
                                        },
                                    )
                                }
                            }
                        }
                        items(if (state.activeKind == QualityKind.VIDEO && selectedVideoFormat != null) {
                            state.visibleOptions.filter { it.container.equals(selectedVideoFormat, true) }
                        } else state.visibleOptions, key = { it.id }) { option ->
                            QualityRow(option, option.id == state.selectedId, { viewModel.select(option.id) }, enabled = !state.enqueuing)
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
                        PlaqueButton(

                            onClick = { if (state.selected?.silentVideo == true) confirmSilent = true else viewModel.download() },
                            enabled = state.selected != null && !state.enqueuing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            if (state.enqueuing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = com.fetchly.app.ui.components.FetchlyButtonColors.content)
                            } else {
                                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(if (state.enqueuing) "Adding to downloads…" else "DOWNLOAD")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(media: MediaInfo, modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (media.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = media.thumbnailUrl,
                contentDescription = "Preview of ${media.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
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

@Composable
private fun PlatformBadge(platform: Platform) {
    val label = PlatformDetector.label(platform)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlatformIcon(platform)

        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun factsFor(media: MediaInfo): String = buildList {
    media.uploader?.let(::add)
    media.durationSeconds?.let { add(formatDuration(it)) }
    // The platform is already shown below the preview.
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
