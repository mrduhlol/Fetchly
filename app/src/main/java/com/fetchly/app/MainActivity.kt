package com.fetchly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fetchly.app.data.AppGraph
import com.fetchly.app.domain.model.ThemeChoice
import com.fetchly.app.ui.navigation.FetchlyNav
import com.fetchly.app.ui.theme.FetchlyTheme

class MainActivity : ComponentActivity() {

    private val graph: AppGraph get() = (application as FetchlyApp).graph

    /** Notification permission prompt; the ask happens once, quietly. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncoming(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            val choice by graph.settings.theme
                .collectAsStateWithLifecycle(initialValue = ThemeChoice.SYSTEM)
            FetchlyTheme(
                darkTheme = when (choice) {
                    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                    ThemeChoice.LIGHT -> false
                    ThemeChoice.DARK -> true
                },
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FetchlyNav(graph)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Share sheets and VIEW links both land in the same inbox. */
    private fun handleIncoming(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let(graph.incoming::share)
                }
            }
            Intent.ACTION_VIEW -> intent.dataString?.let(graph.incoming::share)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
