package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    object Home : Screen("home", "Catalog", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Player : Screen("player/{encodedPath}", "Player") {
        fun createRoute(path: String) = "player/${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
}

@Composable
fun MainNavigationShell(
    viewModel: VideoViewModel,
    settingsViewModel: com.example.videolibrarymanager.ui.SettingsViewModel,
    onClearDatabase: () -> Unit
) {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf(Screen.Home.route) }

    // Synchronize current route on destination changes
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            backStackEntry.destination.route?.let { currentRoute = it }
        }
    }

    val showBottomBar = !currentRoute.startsWith("player/")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(Screen.Home, Screen.Search, Screen.Settings)
                    items.forEach { screen ->
                        if (screen.icon != null) {
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            val onVideoClick: (com.example.videolibrarymanager.data.VideoEntity) -> Unit = { video ->
                navController.navigate(Screen.Player.createRoute(video.path)) {
                    launchSingleTop = true
                }
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel            = viewModel,
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onVideoClick = onVideoClick
                )
            }
            composable(Screen.Search.route) {
                VideoSearchScreen(viewModel = viewModel, onVideoClick = onVideoClick)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    videoViewModel    = viewModel,
                    onClearDatabase   = onClearDatabase
                )
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(androidx.navigation.navArgument("encodedPath") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("encodedPath")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (path != null) {
                    VideoPlayerScreen(
                        videoPath = path,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
