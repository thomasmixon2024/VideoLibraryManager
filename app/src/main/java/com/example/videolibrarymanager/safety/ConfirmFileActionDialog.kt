package com.example.videolibrarymanager.safety

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class RiskLevel { LOW, MEDIUM, HIGH }

/**
 * Blocking confirmation for any destructive file action. Every rename,
 * move, or delete button in the app should route through this before
 * calling SafeFileExecutor - never call the executor directly from a
 * button's onClick.
 *
 * HIGH risk actions (bulk delete, anything touching multiple files at once)
 * require typing CONFIRM, not just tapping a button, on purpose - a single
 * mis-tap should not be able to remove files.
 */
@Composable
fun ConfirmFileActionDialog(
    title: String,
    description: String,
    affectedFiles: List<String>,
    risk: RiskLevel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typedConfirm by remember { mutableStateOf("") }
    val needsTyped = risk == RiskLevel.HIGH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description)
                Spacer(Modifier.height(8.dp))
                if (affectedFiles.size <= 5) {
                    affectedFiles.forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("\u2022 ${affectedFiles.size} files affected", style = MaterialTheme.typography.bodySmall)
                }
                if (needsTyped) {
                    Spacer(Modifier.height(12.dp))
                    Text("Type CONFIRM to proceed", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = typedConfirm,
                        onValueChange = { typedConfirm = it },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !needsTyped || typedConfirm.trim().equals("CONFIRM", ignoreCase = true)
            ) { Text(if (risk == RiskLevel.HIGH) "Confirm permanently" else "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
