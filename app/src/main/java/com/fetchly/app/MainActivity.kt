package com.fetchly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.navigation.FetchlyNav
import com.fetchly.app.presentation.settings.SettingsViewModel
import com.fetchly.app.ui.theme.FetchlyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val theme by settingsVm.theme.collectAsState()
            val sharedUrl = remember { mutableStateOf(extractSharedUrl(intent)) }
            FetchlyTheme(mode = theme) {
                FetchlyNav(sharedUrl = sharedUrl.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Navigation reads sharedUrl once at composition; full re-delivery handled
        // by singleTop + re-created composition on next launch. Keep V1 simple.
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val regex = Regex("https?://[^\\s]+")
        return regex.find(text)?.value ?: text.takeIf { it.isNotBlank() }
    }
}
