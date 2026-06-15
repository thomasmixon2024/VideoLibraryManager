package com.example.videolibrarymanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onRescan: () -> Unit,
    onVideoClick: (VideoEntity) -> Unit
) {
    val videos     by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
    val error      by viewModel.error.collectAsStateWithLifecycle()
    val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()

    var videoToEditCategory by remember { mutableStateOf<VideoEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Library ($videoCount)") },
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading        -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null    -> ErrorState(error = error!!, onRetry = viewModel::retry)
                videos.isEmpty() -> EmptyState(onRescan = onRescan)
                else -> VideoList(
                    videos              = videos,
                    onVideoClick        = onVideoClick,
                    onEditCategoryClick = { videoToEditCategory = it }
                )
            }
        }
    }

    if (videoToEditCategory != null) {
        var categoryText by remember { mutableStateOf(videoToEditCategory!!.category) }
        AlertDialog(
            onDismissRequest = { videoToEditCategory = null },
            title = { Text("Edit Category") },
            text = {
                Column {
                    Text(
                        text  = "Assign a custom category tag for organizing your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = categoryText,
                        onValueChange = { categoryText = it },
                        label         = { Text("Category") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val video = videoToEditCategory
                    if (video != null) viewModel.updateVideoCategory(video.id, categoryText.trim())
                    videoToEditCategory = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { videoToEditCategory = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun VideoList(
    videos: List<VideoEntity>,
    onVideoClick: (VideoEntity) -> Unit,
    onEditCategoryClick: (VideoEntity) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(videos, key = { it.id }) { video ->
            VideoCard(video = video, onClick = { onVideoClick(video) }, onEditCategoryClick = { onEditCategoryClick(video) })
        }
    }
}

@Composable
fun VideoCard(video: VideoEntity, onClick: () -> Unit, onEditCategoryClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = video.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.category)
                        if (video.resolution.isNotEmpty()) append(" • ${video.resolution}")
                        if (video.duration > 0) append(" • ${Formatters.formatDuration(video.duration)}")
                        if (video.size > 0) append(" • ${Formatters.formatSize(video.size)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (video.isCorrupt) {
                    Spacer(Modifier.height(4.dp))
                    Text("⚠ Corrupt", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onEditCategoryClick) { Text("🏷️") }
        }
    }
}

@Composable
fun EmptyState(onRescan: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎬", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text("No videos found.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Now")
            }
        }
    }
}

@Composable
fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
