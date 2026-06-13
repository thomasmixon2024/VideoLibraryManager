package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClearDatabase: () -> Unit,
    modifier: Modifier = Modifier
) {
    var autoScanEnabled by remember { mutableStateOf(true) }
    var skipCorruptVideos by remember { mutableStateOf(false) }
    var scanLimitSliderPosition by remember { mutableFloatStateOf(500f) }
    
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(
                    value = autoScanEnabled,
                    onValueChange = { autoScanEnabled = it },
                    role = Role.Switch
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Automated Background Directory Scan", style = MaterialTheme.typography.bodyLarge)
                Text("Watch system storage paths automatically for files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoScanEnabled, onCheckedChange = null)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(
                    value = skipCorruptVideos,
                    onValueChange = { skipCorruptVideos = it },
                    role = Role.Switch
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Skip Corrupted Structures", style = MaterialTheme.typography.bodyLarge)
                Text("Ignore media objects where parsing fails entirely", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = skipCorruptVideos, onCheckedChange = null)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Max Queue Processing Constraints", style = MaterialTheme.typography.bodyLarge)
            Text("Limit scans to a maximum of:  files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = scanLimitSliderPosition,
                onValueChange = { scanLimitSliderPosition = it },
                valueRange = 50f..2000f,
                steps = 39
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { showDeleteConfirmationDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Trash Bin Icon")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Video Catalog Database", fontWeight = FontWeight.Bold)
        }

        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false },
                title = { Text("Reset Video Database?") },
                text = { Text("This will erase all cached video records, classification categories, and metadata structural tags permanently from storage. Files on disk will remain untouched.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmationDialog = false
                            onClearDatabase()
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
