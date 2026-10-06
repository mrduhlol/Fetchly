package com.fetchly.app.presentation.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.presentation.home.HomeViewModel

/**
 * Developer diagnostics. Hidden from normal UI (unlock by tapping the
 * version on About 5 times). Shows no secrets, tokens, or signed URLs —
 * only state the app already holds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val info = ui.info
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            DebugRow("Fetch state", ui.state.name)
            DebugRow("Platform", info?.platform?.name ?: "—")
            DebugRow("Media type", info?.mediaType?.name ?: "—")
            DebugRow("Analysis duration", ui.lastAnalysisMs?.let { "$it ms" } ?: "—")
            DebugRow("Resolver status", ui.lastResolveStatus ?: "—")
            DebugRow("Formats returned", info?.formats?.size?.toString() ?: "—")
            DebugRow("Selected format", ui.selectedFormatId ?: "—")
            DebugRow(
                "Selected size",
                vm.selectedFormat()?.sizeBytes?.let { "$it bytes" } ?: "—",
            )
            DebugRow(
                "Selected container",
                vm.selectedFormat()?.container ?: "—",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Download URLs, tokens, and headers are intentionally never shown here.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
}
