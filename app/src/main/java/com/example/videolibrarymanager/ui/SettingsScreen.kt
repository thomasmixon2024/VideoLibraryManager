package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videolibrarymanager.data.VideoEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    videoViewModel: VideoViewModel,
    onClearDatabase: () -> Unit,
    onNavigateToLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val autoScanEnabled          by settingsViewModel.autoScanEnabled.collectAsStateWithLifecycle()
    val skipCorruptVideos        by settingsViewModel.skipCorruptVideos.collectAsStateWithLifecycle()
    val scanLimitSliderPosition  by settingsViewModel.scanLimit.collectAsStateWithLifecycle()
    val includedFolders          by settingsViewModel.includedFolders.collectAsStateWithLifecycle()

    // Derive sorted unique bucket names from the current video catalog
    val allVideos by videoViewModel.videos.collectAsStateWithLifecycle()
    val allBuckets: List<String> = remember(allVideos) {
        allVideos.map { it.category }.distinct().sorted()
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var folderSectionExpanded        by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {

        // ── Section header ─────────────────────────────────────────────────
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Auto-scan toggle ───────────────────────────────────────────────
        item {
            SettingsToggleRow(
                title = "Automated Background Directory Scan",
                subtitle = "Watch system storage paths automatically for files",
                checked = autoScanEnabled,
                onCheckedChange = { settingsViewModel.setAutoScanEnabled(it) }
            )
        }

        // ── Skip-corrupt toggle ────────────────────────────────────────────
        item {
            SettingsToggleRow(
                title = "Auto-Skip Corrupted Structures",
                subtitle = "Ignore media objects where parsing fails entirely",
                checked = skipCorruptVideos,
                onCheckedChange = { settingsViewModel.setSkipCorruptVideos(it) }
            )
        }

        // ── Scan limit slider ──────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Max Queue Processing Constraints", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Limit scans to a maximum of: ${scanLimitSliderPosition.toInt()} files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = scanLimitSliderPosition,
                    onValueChange = { settingsViewModel.setScanLimit(it) },
                    valueRange = 50f..2000f,
                    steps = 39
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
        }

        // ── Folder filter section header ───────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = folderSectionExpanded,
                        onValueChange = { folderSectionExpanded = it },
                        role = Role.Button
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Scan Folder Filter",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (includedFolders.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge { Text("${includedFolders.size}") }
                        }
                    }
                    Text(
                        text = if (includedFolders.isEmpty())
                            "Scanning all folders"
                        else
                            "${includedFolders.size} of ${allBuckets.size} folder(s) selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (folderSectionExpanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (folderSectionExpanded) "Collapse" else "Expand"
                )
            }
        }

        // ── Folder filter body (animated expand/collapse) ──────────────────
        if (folderSectionExpanded) {
            if (allBuckets.isEmpty()) {
                item {
                    Text(
                        text = "No folders discovered yet. Run a scan first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                    )
                }
            } else {
                // "All folders" reset chip
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select folders to include in scans:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (includedFolders.isNotEmpty()) {
                            TextButton(
                                onClick = { settingsViewModel.clearFolderFilter() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Clear filter",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // One checkbox row per bucket
                items(allBuckets, key = { it }) { bucket ->
                    val isChecked = includedFolders.isEmpty() || bucket in includedFolders
                    FolderFilterRow(
                        bucketName = bucket,
                        checked = isChecked,
                        isFilterActive = includedFolders.isNotEmpty(),
                        onToggle = { settingsViewModel.toggleFolder(bucket) }
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }

        item { HorizontalDivider() }

        // ── Log viewer button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToLog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Run Log")
            }
        }

        // ── Danger zone ────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showDeleteConfirmationDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Trash Bin Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Video Catalog Database", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Reset Video Database?") },
            text = {
                Text(
                    "This will erase all cached video records, classification categories, and " +
                    "metadata structural tags permanently from storage. Files on disk will remain untouched."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        onClearDatabase()
                    }
                ) {
                    Text(
                        "Clear All",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
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

// ── Reusable toggle row ─────────────────────────────────────────────────────
@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ── Individual folder checkbox row ──────────────────────────────────────────
@Composable
private fun FolderFilterRow(
    bucketName: String,
    checked: Boolean,
    isFilterActive: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox
            )
            .padding(start = 32.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            // When no filter is active, all boxes appear checked but in a neutral tinted state
            colors = if (!isFilterActive) CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ) else CheckboxDefaults.colors()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = bucketName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
