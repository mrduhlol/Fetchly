package com.fetchly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.domain.model.ThemeChoice
import com.fetchly.app.ui.share.ShareDownloadScreen
import com.fetchly.app.ui.share.ShareDownloadViewModel
import com.fetchly.app.ui.theme.FetchlyAppTheme

/** A floating share target in the caller's task; never launches the full Fetchly UI. */
class ShareDownloadActivity : ComponentActivity() {
    private var litePendingDownload: (() -> Unit)? = null
    private var permissionAnswered by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (BuildConfig.IS_LITE) {
            litePendingDownload?.invoke()
            litePendingDownload = null
        } else permissionAnswered = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        val width = (resources.configuration.screenWidthDp - 32).coerceIn(240, 400)
        window.setLayout((width * resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        val graph = (application as FetchlyApp).graph
        val sharedText = readSharedText(intent)
        if (BuildConfig.IS_LITE) {
            setContent { EditionUi.Share(graph, sharedText, { action -> startDownload(action) }, { finish() }) }
            return
        }
        setContent {
            val vm: ShareDownloadViewModel = viewModel { ShareDownloadViewModel(sharedText, graph.resolver, graph.queue) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(permissionAnswered) {
                if (permissionAnswered) { permissionAnswered = false; vm.download() }
            }
            val choice by graph.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeChoice.SYSTEM)
            val dark = when (choice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            val close = { vm.cancelCheck(); finish() }
            // Keep the handoff atomic: dismiss only after the queue confirms persistence.
            BackHandler { if (!state.adding) close() }
            LaunchedEffect(state.adding) { setFinishOnTouchOutside(!state.adding) }
            LaunchedEffect(state.receipt) {
                state.receipt?.let {
                    Toast.makeText(this@ShareDownloadActivity, it.disposition.message, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            FetchlyAppTheme(darkTheme = dark) {
                ShareDownloadScreen(state, vm::selectKind, vm::select, vm::check, vm::confirmSilent,
                    vm::retry, { startDownload(vm::download) }, close, vm::selectContainer)
            }
        }
    }

    private fun startDownload(download: () -> Unit) {
        val preferences = getSharedPreferences("share_download", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("asked_for_notifications", false)) {
            preferences.edit().putBoolean("asked_for_notifications", true).apply()
            if (BuildConfig.IS_LITE) litePendingDownload = download
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else download()
    }

    companion object {
        /** Read text only; never dereference a sender's content URI or grant ourselves file access. */
        internal fun readSharedText(intent: Intent?): String {
            if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return ""
            return runCatching {
                val limit = 128 * 1024 + 1
                val text = StringBuilder()
                fun append(value: CharSequence?) {
                    if (value.isNullOrBlank() || text.length >= limit) return
                    if (text.isNotEmpty()) text.append('\n')
                    text.append(value.take((limit - text.length).coerceAtLeast(0)))
                }
                append(intent?.getCharSequenceExtra(Intent.EXTRA_TEXT))
                if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                    intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.take(50)?.forEach(::append)
                }
                val clip = intent?.clipData
                for (index in 0 until minOf(clip?.itemCount ?: 0, 50)) {
                    val item = clip?.getItemAt(index) ?: continue
                    append(item.text)
                    item.uri?.takeIf { it.scheme == "https" || it.scheme == "http" }?.let { append(it.toString()) }
                }
                text.toString()
            }.getOrDefault("")
        }
    }
}
