package com.fetchly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.ui.welcome.WelcomeViewModel
import com.fetchly.app.ui.welcome.WelcomeScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
        if (redirectSharedIntent(intent, finishMain = true)) return
        enableEdgeToEdge()
        handleIncoming(intent)
        setContent {
            val welcomeViewModel: WelcomeViewModel = viewModel { WelcomeViewModel(graph.settings) }
            val welcome by welcomeViewModel.state.collectAsStateWithLifecycle()
            val choice by graph.settings.theme
                .collectAsStateWithLifecycle(initialValue = ThemeChoice.SYSTEM)
            // The welcome page uses the dark Apollo presentation from the design board.
            val dark = if (welcome.completed != true) true else when (choice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            // Status-bar icon lightness follows the in-app theme, not just
            // the system one.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            LaunchedEffect(welcome.completed) {
                if (welcome.completed == true) requestNotificationPermissionIfNeeded()
            }
            FetchlyTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Edge-to-edge: keep every screen clear of the status bar,
                    // cutouts and the gesture bar.
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        when (welcome.completed) {
                            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(painterResource(R.drawable.fetchly_logo), null, Modifier.size(72.dp))
                            }
                            false -> WelcomeScreen(welcome, welcomeViewModel::getStarted)
                            true -> FetchlyNav(graph)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (redirectSharedIntent(intent)) return
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun redirectSharedIntent(incoming: Intent?, finishMain: Boolean = false): Boolean {
        if (incoming?.action != Intent.ACTION_SEND && incoming?.action != Intent.ACTION_SEND_MULTIPLE) return false
        startActivity(Intent(incoming).setClass(this, ShareDownloadActivity::class.java)
            .setFlags(0))
        // A legacy explicit share can create MainActivity on top of the caller.
        if (finishMain) finish()
        return true
    }

    /** VIEW links continue into the full app. */
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
