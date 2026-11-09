package com.fetchly.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fetchly.app.R
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
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val APPEARANCE = "appearance"
}

@Composable
fun FetchlyNav(graph: AppGraph, navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Column(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
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

        composable(Routes.LIBRARY) {
            val viewModel: DownloadsViewModel = viewModel {
                DownloadsViewModel(graph.history, graph.queue, graph.downloadWorks)
            }
            DownloadsScreen(
                viewModel = viewModel,
                libraryOnly = true,
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
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
            )
        }

        composable(Routes.APPEARANCE) {
            val viewModel: SettingsViewModel = viewModel {
                SettingsViewModel(graph.settings, graph.history, graph.engine)
            }
            com.fetchly.app.ui.settings.AppearanceScreen(viewModel) { navController.popBackStack() }
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                engine = graph.engine,
                onBack = { navController.popBackStack() },
            )
        }
    }
    if (route in listOf(Routes.HOME, Routes.DOWNLOADS, Routes.LIBRARY)) {
        FetchlyNavigationDock(route = route) { destination ->
            if (route != destination) navController.navigate(destination) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                // Home is the retained root; restoring its saved pop can reopen the previous tab.
                restoreState = destination != Routes.HOME
            }
        }
    }
    }
}

/** Shared inset surface; selection moves without shifting the tab targets or labels. */
@Composable
private fun FetchlyNavigationDock(route: String?, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Triple(Routes.HOME, "Home", R.drawable.ic_nav_home),
        Triple(Routes.DOWNLOADS, "Downloads", R.drawable.ic_stat_download),
        Triple(Routes.LIBRARY, "Library", R.drawable.ic_library_folder),
    )
    val selectedIndex = destinations.indexOfFirst { it.first == route }.coerceAtLeast(0)
    val indicatorIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 260),
        label = "Navigation underline",
    )
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerLow,
        ) {
            Box(Modifier.padding(horizontal = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEach { (destination, label, icon) ->
                        val selected = route == destination
                        val contentColor = if (selected) colors.secondary else colors.onSurfaceVariant
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .heightIn(min = 70.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onNavigate(destination) },
                                )
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                        ) {
                            Icon(painterResource(icon), null, Modifier.size(23.dp), tint = contentColor)
                            Text(
                                text = label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val tabWidth = size.width / destinations.size
                    val position = if (isRtl) destinations.lastIndex - indicatorIndex else indicatorIndex
                    val width = 28.dp.toPx().coerceAtMost(tabWidth)
                    val height = 3.dp.toPx()
                    drawRoundRect(
                        color = colors.secondary,
                        topLeft = Offset(tabWidth * (position + 0.5f) - width / 2, size.height - height),
                        size = Size(width, height * 2),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
    }
}
