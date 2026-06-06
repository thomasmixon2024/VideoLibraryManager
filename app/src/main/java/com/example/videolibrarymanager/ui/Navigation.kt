package com.example.videolibrarymanager.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.videolibrarymanager.util.BugLogger

private const val TAG = "AppNavigation"

@Composable
fun AppNavigation(viewModel: VideoViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            BugLogger.debug(TAG, "Navigated → home")
            HomeScreen(
                viewModel          = viewModel,
                onNavigateToSettings = {
                    BugLogger.debug(TAG, "Navigating → settings")
                    nav.navigate("settings")
                }
            )
        }
        composable("settings") {
            BugLogger.debug(TAG, "Navigated → settings")
            SettingsScreen(
                onBack   = { nav.popBackStack() },
                onViewLog = {
                    BugLogger.debug(TAG, "Navigating → log_viewer")
                    nav.navigate("log_viewer")
                }
            )
        }
        composable("log_viewer") {
            BugLogger.debug(TAG, "Navigated → log_viewer")
            LogViewerScreen(onBack = { nav.popBackStack() })
        }
    }
}
