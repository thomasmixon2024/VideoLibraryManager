package com.example.videolibrarymanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onViewLog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            SettingsItem(
                title    = "View Run / Bug Log",
                subtitle = "Browse the full operation log and error history",
                onClick  = onViewLog
            )

            HorizontalDivider()

            SettingsItem(
                title    = "Scan Paths",
                subtitle = "Configure which folders are scanned (coming soon)",
                onClick  = {}
            )

            HorizontalDivider()

            SettingsItem(
                title    = "Quarantine Manager",
                subtitle = "Review and manage corrupt video files (coming soon)",
                onClick  = {}
            )
        }
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle,
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
