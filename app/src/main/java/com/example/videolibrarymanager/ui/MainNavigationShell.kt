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

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Catalog", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainNavigationShell(
    viewModel: VideoViewModel,
    onVideoClick: (com.example.videolibrarymanager.data.VideoEntity) -> Unit,
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(Screen.Home, Screen.Search, Screen.Settings)
                items.forEach { screen ->
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                // Placeholder fallback text for your main catalog layout feed component
                Text(
                    text = "Main Video Catalog Feed Grid goes here.", 
                    modifier = Modifier.padding(androidx.compose.ui.unit.dp.apply { 16 })
                )
            }
            composable(Screen.Search.route) {
                VideoSearchScreen(viewModel = viewModel, onVideoClick = onVideoClick)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onClearDatabase = onClearDatabase)
            }
        }
    }
}
