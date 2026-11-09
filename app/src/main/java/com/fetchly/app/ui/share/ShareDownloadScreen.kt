package com.fetchly.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import coil.compose.SubcomposeAsyncImage
import com.fetchly.app.ui.components.PlaqueOutlinedButton
import com.fetchly.app.ui.components.PlaqueButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.util.FormatSize

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareDownloadScreen(state: ShareDownloadState, onKind: (QualityKind) -> Unit,
    onQuality: (String) -> Unit, onSource: (String) -> Unit, onSilent: (Boolean) -> Unit,
    onRetry: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit,
    onContainer: (String) -> Unit = {}) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .8f).dp
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Download with Fetchly", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.urls.size > 1) {
                    var sourceMenu by remember { mutableStateOf(false) }
                    Box {
                        PlaqueOutlinedButton(onClick = { sourceMenu = true }, enabled = !state.adding) { Text("Shared link ${state.urls.indexOf(state.sourceUrl) + 1} of ${state.urls.size}") }
                        DropdownMenu(sourceMenu, { sourceMenu = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                            state.urls.forEachIndexed { index, url -> DropdownMenuItem(text = { Text("${index + 1}. $url", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                onClick = { sourceMenu = false; onSource(url) }) }
                        }
                    }
                }
                if (state.checking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(state.stage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.media?.let { media ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (media.thumbnailUrl != null) {
                            SubcomposeAsyncImage(
                                model = media.thumbnailUrl,
                                contentDescription = "Preview of ${media.title}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                },
                                error = { ThumbnailFallback() },
                            )
                        } else {
                            ThumbnailFallback()
                        }
                    }
                    Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(PlatformDetector.label(media.platform), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        media.options.map { it.kind }.distinct().forEach { kind ->
                            FilterChip(state.kind == kind, { onKind(kind) }, enabled = !state.adding,
                                label = { Text(when (kind) { QualityKind.VIDEO -> "Video"; QualityKind.AUDIO -> "Audio"; QualityKind.FILE -> if (media.options.any { it.container == "zip" }) "Bulk ZIP" else "Original file" }) })
                        }
                    }
                    if (state.kind == QualityKind.VIDEO) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("mp4", "mkv").forEach { container ->
                                FilterChip(state.selected?.container == container, { onContainer(container) },
                                    enabled = !state.adding && media.options.any { it.kind == QualityKind.VIDEO && it.container == container },
                                    label = { Text(container.uppercase()) })
                            }
                        }
                    }
                    if (state.options.isNotEmpty()) {
                        var qualityMenu by remember(state.kind) { mutableStateOf(false) }
                        Box {
                            PlaqueOutlinedButton(onClick = { qualityMenu = true }, enabled = !state.adding, modifier = Modifier.fillMaxWidth()) {
                                Text(state.selected?.let { "${it.label} · ${it.container.uppercase()} · ${FormatSize.formatApprox(it.sizeBytes) ?: "Size unknown"}" } ?: "Choose quality")
                            }
                            DropdownMenu(qualityMenu, { qualityMenu = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                                state.options.forEach { option ->
                                    DropdownMenuItem(text = { Text("${option.label} · ${option.container.uppercase()} · ${FormatSize.formatApprox(option.sizeBytes) ?: "Size unknown"}") },
                                        onClick = { qualityMenu = false; onQuality(option.id) })
                                }
                            }
                        }
                    }
                    state.selected?.let { option ->
                        Text(listOfNotNull(FormatSize.formatApprox(option.sizeBytes) ?: "Size unknown").joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (option.silentVideo) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(state.silentConfirmed, onSilent, enabled = !state.adding)
                                Text("Download this video without sound", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("Only formats available from the source are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (state.sourceUrl != null) TextButton(onClick = onRetry, enabled = !state.adding && !state.checking) { Text("Retry") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !state.adding) { Text("Cancel") }
                PlaqueButton(onClick = onDownload, enabled = state.canDownload) {
                    if (state.adding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = com.fetchly.app.ui.components.FetchlyButtonColors.content)
                    else Text("Download")
                }
            }
        }
    }
}

@Composable
private fun ThumbnailFallback() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
