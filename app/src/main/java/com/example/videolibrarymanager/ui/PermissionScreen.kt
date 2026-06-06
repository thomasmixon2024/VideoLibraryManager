package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(onGrantClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Video Library Manager",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To organize your Movies folder, this app needs access to your videos.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGrantClicked) {
            Text("Grant Storage Permission")
        }
    }
}
