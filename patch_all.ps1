# =============================================================
#  VideoLibraryManager — Full TODO Patch Script
#  Run from repo root: .\patch_all.ps1
# =============================================================

$root = $PSScriptRoot | Split-Path -Parent
Set-Location $root
Write-Host "Working in: $(Get-Location)" -ForegroundColor Cyan

# ── Helper ────────────────────────────────────────────────────
function Write-File($path, $content) {
    $dir = Split-Path $path
    if ($dir -and !(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -Path $path -Value $content -Encoding UTF8
    Write-Host "  ✓ $path" -ForegroundColor Green
}

# =============================================================
# 1. VideoDao — add clearAllVideos()
# =============================================================
Write-Host "`n[1/8] VideoDao.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\data\VideoDao.kt" @'
package com.example.videolibrarymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isCorrupt = 1")
    fun getQuarantinedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT COUNT(*) FROM videos")
    fun getVideoCount(): Flow<Int>

    @Transaction
    @Query("""
        SELECT v.* FROM videos v
        JOIN videos_fts fts ON v.id = fts.docid
        WHERE videos_fts MATCH :query
        ORDER BY bm25(videos_fts) DESC
        LIMIT 50
    """)
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("UPDATE videos SET thumbnailPath = :thumbPath WHERE id = :id")
    suspend fun updateThumbnail(id: Long, thumbPath: String)

    @Query("UPDATE videos SET checksum = :checksum, isCorrupt = :isCorrupt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateIntegrity(id: Long, checksum: String?, isCorrupt: Boolean, errorMessage: String?)

    @Query("DELETE FROM videos")
    suspend fun clearAllVideos()
}
'@

# =============================================================
# 2. VideoRepository — add searchVideos + clearAllVideos
# =============================================================
Write-Host "`n[2/8] VideoRepository.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\data\VideoRepository.kt" @'
package com.example.videolibrarymanager.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class VideoRepository(context: Context) {

    private val dao: VideoDao = VideoDatabase.getDatabase(context).videoDao()

    fun getAllVideos(): Flow<List<VideoEntity>> = dao.getAllVideos()

    fun getVideoCount(): Flow<Int> = dao.getVideoCount()

    fun searchVideos(query: String): Flow<List<VideoEntity>> = dao.searchVideos(query)

    suspend fun insert(video: VideoEntity) = dao.insert(video)

    suspend fun clearAllVideos() = dao.clearAllVideos()
}
'@

# =============================================================
# 3. VideoViewModel — fix searchVideos + allVideos alias
# =============================================================
Write-Host "`n[3/8] VideoViewModel.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\ui\VideoViewModel.kt" @'
package com.example.videolibrarymanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.util.BugLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)

    private val _videos    = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videos: StateFlow<List<VideoEntity>> = _videos.asStateFlow()

    /** Alias used by VideoSearchScreen when query is blank */
    val allVideos: Flow<List<VideoEntity>> get() = repository.getAllVideos()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()

    init {
        BugLogger.debug(TAG, "init — starting video observation")
        loadVideos()
        observeCount()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            BugLogger.info(TAG, "loadVideos() — collecting getAllVideos flow")
            try {
                repository.getAllVideos()
                    .onEach  { list -> BugLogger.debug(TAG, "Flow emission: ${list.size} video(s)"); _isLoading.value = false }
                    .catch   { e    -> BugLogger.error(TAG, "Error in getAllVideos flow", e); _error.value = e.message; _isLoading.value = false }
                    .collect { list -> _videos.value = list }
            } catch (e: Exception) {
                BugLogger.error(TAG, "loadVideos() threw", e)
                _error.value     = e.message
                _isLoading.value = false
            }
        }
    }

    private fun observeCount() {
        viewModelScope.launch {
            repository.getVideoCount().collect { count ->
                BugLogger.debug(TAG, "Video count updated: $count")
                _videoCount.value = count
            }
        }
    }

    /** FTS5-backed search — returns a live Flow */
    fun searchVideos(query: String): Flow<List<VideoEntity>> = repository.searchVideos(query)

    fun retry() {
        BugLogger.info(TAG, "retry() called")
        loadVideos()
    }

    override fun onCleared() {
        super.onCleared()
        BugLogger.debug(TAG, "onCleared")
    }

    companion object { private const val TAG = "VideoViewModel" }
}
'@

# =============================================================
# 4. ThumbnailHelper — generate thumbnails via MediaMetadataRetriever
# =============================================================
Write-Host "`n[4/8] ThumbnailHelper.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\util\ThumbnailHelper.kt" @'
package com.example.videolibrarymanager.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Generates thumbnail images for video files using MediaMetadataRetriever.
 * Thumbnails are stored as JPEG files in the app's internal cache directory.
 */
object ThumbnailHelper {

    private const val TAG = "ThumbnailHelper"
    private const val THUMB_DIR = "thumbnails"
    private const val THUMB_QUALITY = 85

    /**
     * Returns the thumbnail file path for the given video, generating it if needed.
     * Returns null if generation fails.
     */
    fun getOrCreate(context: Context, videoPath: String, videoId: Long): String? {
        val thumbFile = thumbFile(context, videoId)
        if (thumbFile.exists()) return thumbFile.absolutePath

        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            // Seek to 10% of duration for a representative frame
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs  = durationStr?.toLongOrNull() ?: 0L
            val seekUs      = (durationMs * 0.1 * 1000).toLong().coerceAtLeast(0L)
            val bitmap: Bitmap? = retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (bitmap == null) {
                BugLogger.warn(TAG, "No frame extracted for $videoPath")
                return null
            }

            thumbFile.parentFile?.mkdirs()
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            }
            bitmap.recycle()
            BugLogger.debug(TAG, "Thumbnail saved: ${thumbFile.absolutePath}")
            thumbFile.absolutePath
        } catch (e: Exception) {
            BugLogger.error(TAG, "Failed to generate thumbnail for $videoPath", e)
            null
        }
    }

    /** Delete all cached thumbnails */
    fun clearAll(context: Context) {
        val dir = File(context.cacheDir, THUMB_DIR)
        if (dir.exists()) dir.deleteRecursively()
        BugLogger.info(TAG, "Thumbnail cache cleared")
    }

    private fun thumbFile(context: Context, videoId: Long): File =
        File(context.cacheDir, "$THUMB_DIR/thumb_$videoId.jpg")
}
'@

# =============================================================
# 5. ChecksumHelper — SHA-256 file integrity
# =============================================================
Write-Host "`n[5/8] ChecksumHelper.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\util\ChecksumHelper.kt" @'
package com.example.videolibrarymanager.util

import java.io.File
import java.security.MessageDigest

/**
 * Computes SHA-256 checksums for video files to detect corruption.
 */
object ChecksumHelper {

    private const val TAG = "ChecksumHelper"
    private const val BUFFER_SIZE = 8192

    /**
     * Returns hex SHA-256 of the file, or null on failure.
     * Also returns isCorrupt=true if the file cannot be read at all.
     */
    fun compute(path: String): Result {
        val file = File(path)
        if (!file.exists()) return Result(null, isCorrupt = true, error = "File not found: $path")
        if (!file.canRead()) return Result(null, isCorrupt = true, error = "File not readable: $path")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(BUFFER_SIZE).use { stream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            BugLogger.debug(TAG, "SHA-256[$path] = $hex")
            Result(checksum = hex, isCorrupt = false, error = null)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Checksum failed for $path", e)
            Result(null, isCorrupt = true, error = e.message)
        }
    }

    data class Result(val checksum: String?, val isCorrupt: Boolean, val error: String?)
}
'@

# =============================================================
# 6. VideoScanner — integrate ThumbnailHelper + ChecksumHelper
# =============================================================
Write-Host "`n[6/8] VideoScanner.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\scanner\VideoScanner.kt" @'
package com.example.videolibrarymanager.scanner

import android.content.Context
import android.provider.MediaStore
import com.example.videolibrarymanager.data.VideoDao
import com.example.videolibrarymanager.data.VideoDatabase
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.BugLogger
import com.example.videolibrarymanager.util.ChecksumHelper
import com.example.videolibrarymanager.util.ThumbnailHelper

/**
 * VideoScanner — queries MediaStore, generates thumbnails via
 * MediaMetadataRetriever, and computes SHA-256 checksums for
 * corrupt-file detection.
 */
class VideoScanner(private val context: Context) {

    private val dao: VideoDao by lazy { VideoDatabase.getDatabase(context).videoDao() }

    /**
     * Scans all video files via MediaStore.
     * After inserting each entity, enriches it with thumbnail + checksum
     * and updates the DB row in-place.
     */
    suspend fun scanAll(): List<VideoEntity> {
        BugLogger.debug(TAG, "scanAll() — querying MediaStore")
        val results = mutableListOf<VideoEntity>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idxName     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val idxData     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val idxDuration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val idxSize     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val idxDate     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val idxBucket   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val idxHeight   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val idxWidth    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)

                var skipped = 0
                while (cursor.moveToNext()) {
                    val path = cursor.getString(idxData)
                    if (path.isNullOrBlank()) { skipped++; continue }

                    val width  = cursor.getInt(idxWidth)
                    val height = cursor.getInt(idxHeight)

                    results += VideoEntity(
                        name       = cursor.getString(idxName) ?: "Unknown",
                        path       = path,
                        category   = cursor.getString(idxBucket) ?: "Uncategorized",
                        duration   = cursor.getLong(idxDuration),
                        size       = cursor.getLong(idxSize),
                        resolution = if (width > 0 && height > 0) "${width}x${height}" else "",
                        dateAdded  = cursor.getLong(idxDate) * 1000L,
                    )
                }
                BugLogger.info(TAG, "Cursor exhausted — found=${results.size} skipped=$skipped")
            } ?: BugLogger.warn(TAG, "ContentResolver.query returned null cursor")
        } catch (e: SecurityException) {
            BugLogger.error(TAG, "SecurityException — missing permission?", e)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Unexpected error during MediaStore scan", e)
        }

        return results
    }

    /**
     * After inserting a video entity into Room, call this to generate
     * thumbnail + checksum and persist them back to the DB row.
     */
    suspend fun enrichVideo(entity: VideoEntity) {
        // Thumbnail
        val thumbPath = ThumbnailHelper.getOrCreate(context, entity.path, entity.id)
        if (thumbPath != null) dao.updateThumbnail(entity.id, thumbPath)

        // Checksum + corrupt flag
        val integrity = ChecksumHelper.compute(entity.path)
        dao.updateIntegrity(
            id           = entity.id,
            checksum     = integrity.checksum,
            isCorrupt    = integrity.isCorrupt,
            errorMessage = integrity.error
        )
        BugLogger.debug(TAG, "Enriched ${entity.name} — corrupt=${integrity.isCorrupt}")
    }

    companion object { private const val TAG = "VideoScanner" }
}
'@

# =============================================================
# 7. VideoScannerService — call enrichVideo after insert
# =============================================================
Write-Host "`n[7/8] VideoScannerService.kt" -ForegroundColor Yellow
Write-File "app\src\main\java\com\example\videolibrarymanager\service\VideoScannerService.kt" @'
package com.example.videolibrarymanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.videolibrarymanager.data.VideoDatabase
import com.example.videolibrarymanager.scanner.VideoScanner
import com.example.videolibrarymanager.util.BugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VideoScannerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BugLogger.info(TAG, "onStartCommand — startId=$startId")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Scanning…"))
        serviceScope.launch { scanVideos() }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Video Scanner", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun createNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Library Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .build()

    private suspend fun scanVideos() {
        val startMs = System.currentTimeMillis()
        BugLogger.info(TAG, "─── Scan cycle started ───")
        try {
            val dao     = VideoDatabase.getDatabase(applicationContext).videoDao()
            val scanner = VideoScanner(applicationContext)

            updateNotification("Scanning media library…")
            val videos = withContext(Dispatchers.IO) { scanner.scanAll() }
            BugLogger.info(TAG, "MediaStore returned ${videos.size} video(s)")

            var inserted = 0; var errors = 0
            videos.forEachIndexed { index, v ->
                try {
                    dao.insert(v)
                    inserted++

                    // Enrich with thumbnail + checksum after insert so we have the DB id
                    val saved = dao.getAllVideos().first().find { it.path == v.path }
                    if (saved != null) {
                        updateNotification("Processing ${index + 1}/${videos.size}: ${v.name}")
                        scanner.enrichVideo(saved)
                    }
                } catch (e: Exception) {
                    errors++
                    BugLogger.error(TAG, "Failed to process ${v.path}", e)
                }
            }

            val total   = dao.getVideoCount().first()
            val elapsed = System.currentTimeMillis() - startMs
            BugLogger.info(TAG, "Scan complete — inserted=$inserted errors=$errors total=$total elapsed=${elapsed}ms")

        } catch (e: SecurityException) {
            BugLogger.error(TAG, "SecurityException — permission likely revoked", e)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Unexpected error during scan", e)
        } finally {
            BugLogger.info(TAG, "─── Scan cycle ended ───")
            stopSelf()
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        BugLogger.info(TAG, "onDestroy — serviceScope cancelled")
    }

    companion object {
        private const val TAG             = "VideoScannerService"
        private const val CHANNEL_ID      = "scanner_channel"
        private const val NOTIFICATION_ID = 1
    }
}
'@

# =============================================================
# 8. MainNavigationShell — wire up real HomeScreen
# =============================================================
Write-Host "`n[8/8] MainNavigationShell.kt + HomeScreen.kt" -ForegroundColor Yellow

Write-File "app\src\main\java\com\example\videolibrarymanager\ui\HomeScreen.kt" @'
package com.example.videolibrarymanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.videolibrarymanager.data.VideoEntity
import java.io.File

@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onVideoClick: (VideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos   by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error     by viewModel.error.collectAsStateWithLifecycle()
    val count     by viewModel.videoCount.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {

        // Header bar
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (count > 0) {
                    Badge { Text("$count") }
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error loading library", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.retry() }) { Text("Retry") }
                }
            }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos found. Scan is running…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCatalogCard(video = video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun VideoCatalogCard(video: VideoEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailPath != null && File(video.thumbnailPath).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(video.thumbnailPath)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("VIDEO", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
                // Corrupt badge
                if (video.isCorrupt) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(bottomStart = 6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("!", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (video.resolution.isNotEmpty()) {
                        MetaChip(video.resolution)
                    }
                    if (video.duration > 0) {
                        MetaChip(formatDuration(video.duration))
                    }
                    if (video.size > 0) {
                        MetaChip(formatSize(video.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    else                    -> "%.0f KB".format(bytes / 1_024.0)
}
'@

Write-File "app\src\main\java\com\example\videolibrarymanager\ui\MainNavigationShell.kt" @'
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
import com.example.videolibrarymanager.data.VideoEntity

sealed class Screen(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Home     : Screen("home",     "Catalog",  Icons.Default.Home)
    object Search   : Screen("search",   "Search",   Icons.Default.Search)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainNavigationShell(
    viewModel: VideoViewModel,
    onVideoClick: (VideoEntity) -> Unit,
    onClearDatabase: () -> Unit
) {
    val navController = rememberNavController()
    var currentRoute  by remember { mutableStateOf(Screen.Home.route) }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            entry.destination.route?.let { currentRoute = it }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(Screen.Home, Screen.Search, Screen.Settings).forEach { screen ->
                    NavigationBarItem(
                        icon     = { Icon(screen.icon, contentDescription = screen.title) },
                        label    = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick  = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel, onVideoClick = onVideoClick)
            }
            composable(Screen.Search.route) {
                VideoSearchScreen(viewModel = viewModel, onVideoClick = onVideoClick)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onClearDatabaseClick = onClearDatabase)
            }
        }
    }
}
'@

# =============================================================
# 9. Fix README — placeholder URL + CI badge
# =============================================================
Write-Host "`n[9/9] README.md" -ForegroundColor Yellow
$readme = Get-Content "README.md" -Raw
$readme = $readme -replace 'https://github\.com/your-org/VideoLibraryManager\.git',
                            'https://github.com/thomasmixon2024/VideoLibraryManager.git'
$readme = $readme -replace '\[!\[Android CI\]\([^)]*\)\]\([^)]*\)',
                            '[![Android CI](https://github.com/thomasmixon2024/VideoLibraryManager/actions/workflows/android.yml/badge.svg)](https://github.com/thomasmixon2024/VideoLibraryManager/actions/workflows/android.yml)'
Set-Content "README.md" -Value $readme -Encoding UTF8
Write-Host "  ✓ README.md" -ForegroundColor Green

# =============================================================
# 10. Add Coil dependency to build.gradle.kts if missing
# =============================================================
Write-Host "`n[10/10] Checking Coil dependency" -ForegroundColor Yellow
$gradle = Get-Content "app\build.gradle.kts" -Raw
if ($gradle -notmatch "coil") {
    $gradle = $gradle -replace '(// Coroutines)',
        "// Coil — image loading for thumbnails`n    implementation(`"io.coil-kt:coil-compose:2.6.0`")`n`n    `$1"
    Set-Content "app\build.gradle.kts" -Value $gradle -Encoding UTF8
    Write-Host "  ✓ Coil added to build.gradle.kts" -ForegroundColor Green
} else {
    Write-Host "  ✓ Coil already present" -ForegroundColor Gray
}

# =============================================================
# Done — commit
# =============================================================
Write-Host "`n=== All patches applied. Staging and committing... ===" -ForegroundColor Cyan
git add -A
git status
Write-Host "`nRun: git commit -m 'feat: implement thumbnails, checksums, search, home screen, fix ViewModel' && git push" -ForegroundColor Yellow
