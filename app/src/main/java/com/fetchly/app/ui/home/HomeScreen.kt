package com.fetchly.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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

/**
 * Paste a link, see it detected, fetch it. Clipboard links appear as a chip
 * only when the field is empty; share-sheet links arrive through the
 * view model's incoming flow.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenResult: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Fetchly", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Paste. Choose. Fetch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Downloads")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        UrlInput(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            enabled = !state.analyzing,
            onGo = viewModel::fetch,
        )

        state.detected?.let { platform ->
            Spacer(Modifier.height(8.dp))
            Text(
                detectedLine(platform),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.suggestion?.let { url ->
            Spacer(Modifier.height(12.dp))
            ClipboardSuggestion(
                url = url,
                onAccept = viewModel::acceptSuggestion,
                onDismiss = viewModel::dismissSuggestion,
            )
        }

        Spacer(Modifier.height(20.dp))

        FetchButton(
            loading = state.analyzing,
            enabled = state.canFetch,
            onClick = viewModel::fetch,
        )

        state.status?.let { stage ->
            Spacer(Modifier.height(12.dp))
            StatusCard(message = stage, isError = false)
        }
        state.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            StatusCard(message = message, isError = true)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Everything runs on this device — resolution, download and merge. " +
                "No servers, no accounts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    "From your clipboard",
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
    Capability.SUPPORTED -> "${PlatformDetector.label(platform)} link — fully supported."
    Capability.BEST_EFFORT -> "${PlatformDetector.label(platform)} link — best effort."
}
