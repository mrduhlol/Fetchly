package com.fetchly.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fetchly.app.presentation.about.AboutScreen
import com.fetchly.app.presentation.downloads.DownloadsScreen
import com.fetchly.app.presentation.home.HomeScreen
import com.fetchly.app.presentation.home.HomeViewModel
import com.fetchly.app.presentation.result.ResultScreen
import com.fetchly.app.presentation.settings.SettingsScreen

@Composable
fun FetchlyNav(sharedUrl: String?) {
    val nav = rememberNavController()
    // Shared ViewModel scoped to the nav graph so Home <-> Result share analysis state.
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { backStack ->
            val vm: HomeViewModel = viewModel(backStack)
            // Actually scope to activity so result shares it:
            val activityVm: HomeViewModel = viewModel()
            HomeScreen(
                sharedUrl = sharedUrl,
                onResult = { nav.navigate("result") },
                onDownloads = { nav.navigate("downloads") },
                onSettings = { nav.navigate("settings") },
                vm = activityVm,
            )
        }
        composable("result") {
            val activityVm: HomeViewModel = viewModel()
            ResultScreen(
                onBack = { nav.popBackStack() },
                onDownloadStarted = { nav.navigate("downloads") },
                vm = activityVm,
            )
        }
        composable("downloads") {
            DownloadsScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onAbout = { nav.navigate("about") },
            )
        }
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() })
        }
    }
}
