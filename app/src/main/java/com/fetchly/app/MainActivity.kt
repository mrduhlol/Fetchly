package com.fetchly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.navigation.FetchlyNav
import com.fetchly.app.presentation.settings.SettingsViewModel
import com.fetchly.app.ui.theme.FetchlyTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Latest URL shared into the app; updated on every intent, including re-delivery. */
    private val sharedUrlFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrlFlow.value = extractSharedUrl(intent)
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val theme by settingsVm.theme.collectAsState()
            val sharedUrl by sharedUrlFlow.collectAsState()
            FetchlyTheme(mode = theme) {
                FetchlyNav(sharedUrl = sharedUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop re-delivery: push into the flow so Home prefills immediately.
        extractSharedUrl(intent)?.let { sharedUrlFlow.value = it }
    }

    /**
     * Handles ACTION_SEND text/plain: extracts the first URL from
     * whitespace-separated text. Empty or URL-less shares yield null.
     */
    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return null
        val regex = Regex("https?://[^\\s]+")
        return regex.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')
    }
}
