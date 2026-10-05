package com.fetchly.app.presentation.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.domain.model.FetchState
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.presentation.components.FetchButton
import com.fetchly.app.presentation.components.UrlInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedUrl: String?,
    onResult: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }

    // Ask for notification permission once (Android 13+); downloads work without it.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(sharedUrl) {
        clipboardSuggestion = null
        if (!sharedUrl.isNullOrBlank()) vm.prefill(sharedUrl)
    }

    // Peek at the clipboard only on resume — never monitored in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumeScope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeScope.launch {
                    clipboardSuggestion = peekClipboardUrl(context, vm.ui.value.url)
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.state) {
        if (ui.state == FetchState.READY && ui.info != null && ui.duplicateOf == null) onResult()
        if (ui.state == FetchState.ERROR && ui.error != null) snacks.showSnackbar(ui.error!!)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                title = { Text("Fetchly") },
                actions = {
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Fetchly", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste. Choose. Fetch.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            UrlInput(
                value = ui.url,
                onChange = { vm.onUrlChange(it); clipboardSuggestion = null },
                onPaste = {
                    pasteFromClipboard(context) { vm.prefill(it) }
                },
                onFetch = vm::fetch,
            )
            clipboardSuggestion?.let { suggestion ->
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Link detected from clipboard",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            vm.prefill(suggestion)
                            clipboardSuggestion = null
                        }) { Text("Paste") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val detected = remember(ui.url) { PlatformDetector.detect(ui.url) }
            Text(
                if (ui.url.isBlank()) "Supports supported public media sources"
                else if (detected.name == "UNKNOWN") "This source isn't currently supported."
                else "Detected: ${detected.label}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (ui.state == FetchState.ANALYZING) {
                CircularProgressIndicator()
            } else {
                FetchButton(label = "FETCH", onClick = vm::fetch, enabled = ui.url.isNotBlank())
            }
            if (ui.state == FetchState.COMPLETED) {
                Spacer(Modifier.height(12.dp))
                Text("Download started — track it in Downloads.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun pasteFromClipboard(context: Context, onUrl: (String) -> Unit) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip: ClipData? = cm.primaryClip
    val text = (0 until (clip?.itemCount ?: 0))
        .asSequence()
        .mapNotNull { clip?.getItemAt(it)?.text?.toString() }
        .firstOrNull { it.contains("http") }
    if (text != null) onUrl(text.trim())
}

private suspend fun peekClipboardUrl(context: Context, currentInput: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return@runCatching null
            (0 until clip.itemCount).asSequence()
                .mapNotNull { clip.getItemAt(it)?.text?.toString() }
                .map { it.trim() }
                .firstOrNull { it.contains("http") && it != currentInput }
        }.getOrNull()
    }
