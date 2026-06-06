package com.example.videolibrarymanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.content.Intent
import com.example.videolibrarymanager.util.BugLogger

/**
 * LogViewerScreen — displays the live run/bug log in a scrollable monospace view.
 *
 * Accessible via Settings → View Run Log.
 *
 * Features:
 * • Vertical + horizontal scroll (log lines can be long)
 * • Share button — sends the log file via Android share sheet
 * • Clear button — erases the log and resets with a new session header
 * • Auto-refreshes once when first composed; user can pull-to-refresh manually
 *   by tapping the reload FAB (simple workaround without SwipeRefresh dependency)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(BugLogger.readLog()) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run / Bug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share log file
                    IconButton(onClick = {
                        val file = BugLogger.logFile()
                        if (file != null && file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Log"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share log")
                    }

                    // Clear log
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear log")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                logText = BugLogger.readLog()
                BugLogger.debug("LogViewerScreen", "Log refreshed by user")
            }) {
                Text("↻", fontSize = 20.sp)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (logText.isBlank()) {
                Text(
                    "Log is empty.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                Text(
                    text = logText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Log?") },
            text  = { Text("This will erase all recorded log entries and start a new session.") },
            confirmButton = {
                TextButton(onClick = {
                    BugLogger.clear()
                    logText = BugLogger.readLog()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}
