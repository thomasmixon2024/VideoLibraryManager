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
            BugLogger.debug(TAG, "Notification channel created: $CHANNEL_ID")
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
            val settings = com.example.videolibrarymanager.data.SettingsRepository(applicationContext)
            
            val autoScan = settings.autoScanEnabled.first()
            val skipCorrupt = settings.skipCorruptVideos.first()
            val scanLimit = settings.scanLimit.first().toInt()
            val includedFolders = settings.includedFolders.first()
            
            if (!autoScan) {
                BugLogger.info(TAG, "Auto-scan disabled by user preferences. Exiting.")
                return
            }

            BugLogger.info(TAG, "Running MediaStore scan with limit $scanLimit, folders=${if (includedFolders.isEmpty()) "all" else includedFolders}…")
            var videos = withContext(Dispatchers.IO) { scanner.scanAll(scanLimit, includedFolders) }
            BugLogger.info(TAG, "MediaStore returned ${videos.size} video(s)")
            
            if (skipCorrupt) {
                val initialSize = videos.size
                videos = videos.filter { !it.isCorrupt }
                if (initialSize != videos.size) {
                    BugLogger.info(TAG, "Skipped ${initialSize - videos.size} corrupt videos based on preferences")
                }
            }

            if (videos.isEmpty()) {
                BugLogger.warn(TAG, "No valid videos found to insert.")
            }

            if (videos.isNotEmpty()) {
                val existingVideos = dao.getAllVideos().first()
                val pathMap = existingVideos.associateBy { it.path }
                val mergedVideos = videos.map { scanned ->
                    val existing = pathMap[scanned.path]
                    if (existing != null) {
                        scanned.copy(id = existing.id, category = existing.category)
                    } else {
                        scanned
                    }
                }
                dao.insertAll(mergedVideos)
                BugLogger.info(TAG, "Bulk inserted/replaced ${mergedVideos.size} videos (preserved categories)")
            }

            val total   = dao.getVideoCount().first()
            val elapsed = System.currentTimeMillis() - startMs
            BugLogger.info(TAG, "Scan complete — scanned=${videos.size} total_in_db=$total elapsed=${elapsed}ms")

        } catch (e: SecurityException) {
            BugLogger.error(TAG, "SecurityException — permission likely revoked at runtime", e)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Unexpected error during scan", e)
        } finally {
            BugLogger.info(TAG, "─── Scan cycle ended — stopping service ───")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        BugLogger.info(TAG, "onDestroy — serviceScope cancelled")
    }

    companion object {
        private const val TAG           = "VideoScannerService"
        private const val CHANNEL_ID    = "scanner_channel"
        private const val NOTIFICATION_ID = 1
    }
}
