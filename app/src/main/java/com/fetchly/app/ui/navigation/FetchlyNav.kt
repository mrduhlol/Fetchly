package com.fetchly.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fetchly.app.data.AppGraph
import com.fetchly.app.ui.about.AboutScreen
import com.fetchly.app.ui.downloads.DownloadsScreen
import com.fetchly.app.ui.downloads.DownloadsViewModel
import com.fetchly.app.ui.home.HomeScreen
import com.fetchly.app.ui.home.HomeViewModel
import com.fetchly.app.ui.result.ResultScreen
import com.fetchly.app.ui.result.ResultViewModel
import com.fetchly.app.ui.settings.SettingsScreen
import com.fetchly.app.ui.settings.SettingsViewModel

object Routes {
    const val BATCH = "batch"
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
                onOpenBatch = { navController.navigate(Routes.BATCH) },
                onOpenResult = { navController.navigate(Routes.RESULT) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.BATCH) {
            val vm: com.fetchly.app.ui.batch.BatchViewModel = viewModel {
                com.fetchly.app.ui.batch.BatchViewModel(graph.resolver, graph.session, graph.queue)
            }
            com.fetchly.app.ui.batch.BatchScreen(vm, { navController.popBackStack() }, { navController.navigate(Routes.DOWNLOADS) })
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
                    navController.getBackStackEntry(Routes.HOME).savedStateHandle.set("downloadNotice", viewModel.state.value.queuedMessage)
                    navController.navigate(Routes.DOWNLOADS) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(Routes.DOWNLOADS) { backStackEntry ->
            val viewModel: DownloadsViewModel = viewModel {
                DownloadsViewModel(graph.history, graph.queue, graph.downloadWorks)
            }
            val notice = androidx.compose.runtime.remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME).savedStateHandle.remove<String>("downloadNotice") }
            DownloadsScreen(
                initialNotice = notice,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onChooseFormat = { url ->
                    graph.incoming.share(url)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel {
                SettingsViewModel(graph.settings, graph.history, graph.engine)
            }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                engine = graph.engine,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
