package com.fetchly.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fetchly.app.data.AppGraph
import com.fetchly.app.ui.components.EmptyState
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.home.HomeScreen
import com.fetchly.app.ui.home.HomeViewModel
import com.fetchly.app.ui.result.ResultScreen
import com.fetchly.app.ui.result.ResultViewModel

object Routes {
    const val HOME = "home"
    const val RESULT = "result"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun FetchlyNav(graph: AppGraph, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel {
                HomeViewModel(graph.resolver, graph.session, graph.incoming)
            }
            HomeScreen(
                viewModel = viewModel,
                onOpenResult = { navController.navigate(Routes.RESULT) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.RESULT) {
            // Guard: arriving without a fresh analysis (process death, a
            // restored back stack) has nothing honest to show.
            val media by graph.session.info.collectAsStateWithLifecycle()
            if (media == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: ResultViewModel = viewModel {
                ResultViewModel(graph.session, graph.settings.defaultQuality, graph.queue)
            }
            ResultScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onQueued = {
                    navController.navigate(Routes.DOWNLOADS) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(Routes.DOWNLOADS) { DownloadsPlaceholder(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsPlaceholder(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutPlaceholder(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun DownloadsPlaceholder(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Downloads", onBack = onBack)
        EmptyState(
            icon = Icons.Filled.PlayArrow,
            title = "No downloads yet",
            subtitle = "Links you fetch will appear here with their progress.",
        )
    }
}

@Composable
private fun SettingsPlaceholder(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings", onBack = onBack)
        EmptyState(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "This screen arrives with the next build step.",
        )
    }
}

@Composable
private fun AboutPlaceholder(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("About", onBack = onBack)
        EmptyState(
            icon = Icons.Filled.Settings,
            title = "Fetchly 2.0",
            subtitle = "This screen arrives with the next build step.",
        )
    }
}
