package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videolibrarymanager.data.VideoEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onNavigateToSettings: () -> Unit
) {
    val videos     by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
    val error      by viewModel.error.collectAsStateWithLifecycle()
    val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Library ($videoCount)") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙️")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                isLoading  -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> ErrorState(error = error!!, onRetry = viewModel::retry)
                videos.isEmpty() -> EmptyState()
                else -> VideoList(videos)
            }
        }
    }
}

@Composable
fun VideoList(videos: List<VideoEntity>) {
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(videos, key = { it.id }) { video ->
            VideoCard(video)
        }
    }
}

@Composable
fun VideoCard(video: VideoEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text     = video.name,
                style    = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = buildString {
                    append(video.category)
                    if (video.resolution.isNotEmpty()) append(" • ${video.resolution}")
                    if (video.duration > 0) append(" • ${formatDuration(video.duration)}")
                    if (video.size > 0) append(" • ${formatSize(video.size)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (video.isCorrupt) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ Corrupt", color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎬", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text("No videos found.", style = MaterialTheme.typography.bodyLarge)
            Text("Grant storage permission and tap Scan.",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("Error", style = MaterialTheme.typography.titleMedium,
                 color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}
