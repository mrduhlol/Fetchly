package com.fetchly.app

import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.data.AppGraph
import com.fetchly.app.domain.batch.BatchLinks
import com.fetchly.app.lite.*
import com.fetchly.app.ui.downloads.DownloadsViewModel
import com.fetchly.app.ui.settings.SettingsViewModel
import com.fetchly.app.ui.welcome.WelcomeViewModel
import kotlinx.coroutines.delay

/** Edition boundary: full and Lite share download services, but have separate screens. */
object EditionUi {
    @Composable
    fun Root(graph: AppGraph, requestNotifications: (() -> Unit) -> Unit) {
        val welcomeVm: WelcomeViewModel = viewModel { WelcomeViewModel(graph.settings) }
        val welcome by welcomeVm.state.collectAsStateWithLifecycle()
        LiteTheme {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (welcome.completed) {
                        null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Parchment)
                        false -> Welcome(welcome.saving, welcome.error, welcomeVm::getStarted)
                        true -> Main(graph, requestNotifications)
                    }
                }
            }
        }
    }

    @Composable
    private fun Welcome(saving: Boolean, error: String?, start: () -> Unit) {
        val context = LocalContext.current
        val reducedMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        var textVisible by remember { mutableStateOf(reducedMotion) }
        var buttonVisible by rememberSaveable { mutableStateOf(reducedMotion) }
        LaunchedEffect(Unit) { textVisible = true; if (!reducedMotion) delay(2500); buttonVisible = true }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val artHeight = maxWidth * (1601f / 720f)
            val canvasHeight = maxOf(maxHeight, artHeight)
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiteBackdrop(R.drawable.lite_welcome, Modifier.fillMaxWidth().heightIn(min = canvasHeight)) {
                    Column(Modifier.fillMaxWidth().padding(top = artHeight * .43f, start = 28.dp, end = 28.dp, bottom = 150.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        AnimatedVisibility(textVisible, enter = fadeIn(tween(1100))) {
                            Surface(color = Ink, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("FETCHLY LITE", color = Parchment, style = MaterialTheme.typography.headlineMedium)
                                    Text("Paste. Choose. Fetch.", color = Parchment, style = MaterialTheme.typography.titleLarge)
                                    Text("Your favorite moments, ready to keep.\nPaste or share a public media link, choose your format, and download.",
                                        color = Parchment, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                        AnimatedVisibility(buttonVisible, enter = fadeIn(tween(800))) {
                            Button(onClick = start, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Parchment, contentColor = Ink)) {
                                Text(if (saving) "Starting…" else "Get started")
                            }
                        }
                        error?.let { Surface(color = Parchment) { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) } }
                    }
                }
            }
        }
    }

    @Composable
    private fun Main(graph: AppGraph, requestNotifications: (() -> Unit) -> Unit) {
        var route by rememberSaveable { mutableStateOf("home") }
        val vm: LiteResolveViewModel = viewModel { LiteResolveViewModel(graph.resolver, graph.queue, graph.settings.defaultQuality) }
        val state by vm.state.collectAsStateWithLifecycle()
        val downloadsVm: DownloadsViewModel = viewModel { DownloadsViewModel(graph.history, graph.queue, graph.downloadWorks) }
        val incoming by graph.incoming.text.collectAsStateWithLifecycle()
        LaunchedEffect(incoming) { incoming?.let { route = "home"; vm.resolve(it); graph.incoming.consume() } }
        LaunchedEffect(state.complete) { if (state.complete) { route = "downloads"; vm.dismiss() } }
        BackHandler(route != "home") { route = "home" }
        Column(Modifier.fillMaxSize()) {
            val art = when (route) { "downloads" -> R.drawable.lite_downloads; "library" -> R.drawable.lite_library; else -> R.drawable.lite_home }
            if (route == "settings") {
                val settingsVm: SettingsViewModel = viewModel { SettingsViewModel(graph.settings, graph.history, graph.engine) }
                Box(Modifier.weight(1f).background(Ink)) { LiteSettings(settingsVm) { route = "home" } }
            } else {
                Box(Modifier.weight(1f)) {
                    if (route == "home") Home(state, vm) { route = "settings" }
                    else LiteBackdrop(art, Modifier.fillMaxSize()) {
                        Column {
                            Header { route = "settings" }
                            LiteDownloads(downloadsVm, route == "library") { route = "home" }
                        }
                    }
                }
                NavigationBar(containerColor = Ink, contentColor = Parchment, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    listOf(Triple("home", "Home", R.drawable.ic_nav_home), Triple("downloads", "Downloads", R.drawable.ic_stat_download), Triple("library", "Library", R.drawable.ic_library_folder)).forEach { (id, label, icon) ->
                        NavigationBarItem(selected = route == id, onClick = { route = id },
                            icon = { Icon(painterResource(icon), null, Modifier.size(24.dp)) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Parchment, selectedTextColor = Parchment,
                                unselectedIconColor = Color(0xFFC3A98D), unselectedTextColor = Color(0xFFC3A98D), indicatorColor = Color(0xFF473526)))
                    }
                }
            }
        }
        if (state.loading || state.items.isNotEmpty() || state.error != null) LiteFormatDialog(state, vm,
            download = { requestNotifications(vm::download) }, close = vm::dismiss)
    }

    @Composable
    private fun Header(settings: () -> Unit) {
        ParchmentPanel(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.fetchly_logo), "Fetchly Lite Hermes logo", Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("FETCHLY", style = MaterialTheme.typography.titleLarge)
                    Text("LITE", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = settings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
    }

    @Composable
    private fun Home(state: LiteResolveState, vm: LiteResolveViewModel, settings: () -> Unit) {
        val clipboard = LocalClipboardManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(state.input) {
            delay(650)
            if (state.input.isNotBlank() && !state.loading && state.items.isEmpty() && state.error == null &&
                BatchLinks.parse(state.input).urls.size == 1) { keyboard?.hide(); vm.resolve() }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = maxOf(maxHeight, maxWidth * (1601f / 720f))
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiteBackdrop(R.drawable.lite_home, Modifier.fillMaxWidth().heightIn(min = height)) {
                    Column(Modifier.fillMaxWidth()) {
                        Header(settings)
                        ParchmentPanel(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Find it.\nKeep it.", style = MaterialTheme.typography.headlineLarge)
                            Text("Your links. Your media.\nSaved right to your phone.")
                            OutlinedTextField(state.input, vm::input, label = { Text("Media link") }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { keyboard?.hide(); vm.resolve() }))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { clipboard.getText()?.text?.let { vm.resolve(it); keyboard?.hide() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Paste") }
                                OutlinedButton(onClick = { keyboard?.hide(); vm.resolve() }, enabled = state.input.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Fetch") }
                            }
                            Text("Formats appear automatically when you paste.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Share(graph: AppGraph, text: String, requestNotifications: (() -> Unit) -> Unit, close: () -> Unit) {
        val vm: LiteResolveViewModel = viewModel { LiteResolveViewModel(graph.resolver, graph.queue, graph.settings.defaultQuality) }
        val state by vm.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(Unit) { vm.resolve(text) }
        BackHandler { if (!state.adding) close() }
        LaunchedEffect(state.adding) { (context as? android.app.Activity)?.setFinishOnTouchOutside(!state.adding) }
        LaunchedEffect(state.complete) { if (state.complete) {
            Toast.makeText(context, state.notice ?: "Added to downloads.", Toast.LENGTH_SHORT).show(); close()
        } }
        LiteTheme { LiteFormatDialog(state, vm, { requestNotifications(vm::download) }, close, floating = true) }
    }
}
