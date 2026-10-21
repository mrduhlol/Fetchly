package com.fetchly.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.Capability
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.ui.components.FetchButton
import com.fetchly.app.ui.components.StatusCard
import com.fetchly.app.ui.components.UrlInput

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenResult: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fetch = { keyboard?.hide(); viewModel.fetch() }

    LifecycleResumeEffect(Unit) {
        viewModel.offerClipboard(clipboard.getText()?.text)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onOpenResult()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
                .imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("fetchly", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Downloads")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
            Spacer(Modifier.height(36.dp))
            Text("A link.\nYour next download.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "Paste a media link to preview it and choose an available format.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Start with a link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = !state.analyzing,
                            onClick = { clipboard.getText()?.text?.let(viewModel::onInputChange) },
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Paste")
                        }
                    }
                    UrlInput(state.input, viewModel::onInputChange, !state.analyzing, fetch)
                    state.detected?.let {
                        Text(detectedLine(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.suggestion?.let {
                        ClipboardSuggestion(it, viewModel::acceptSuggestion, viewModel::dismissSuggestion)
                    }
                    FetchButton(loading = state.analyzing, enabled = state.canFetch, onClick = fetch)
                    state.status?.let { StatusCard(it, isError = false) }
                    state.error?.let { StatusCard(it, isError = true) }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("LINK → PREVIEW → DOWNLOAD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Review the available quality, file format and reported size before downloading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClipboardSuggestion(
    url: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Paste from clipboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss the clipboard suggestion",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun detectedLine(platform: Platform): String = when (PlatformDetector.capability(platform)) {
    Capability.SUPPORTED -> "${PlatformDetector.label(platform)} link detected"
    Capability.BEST_EFFORT -> "${PlatformDetector.label(platform)} · availability varies by link"
}
