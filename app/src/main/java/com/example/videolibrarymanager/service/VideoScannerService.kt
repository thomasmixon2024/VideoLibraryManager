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

            BugLogger.info(TAG, "Running MediaStore scan…")
            val videos = withContext(Dispatchers.IO) { scanner.scanAll() }
            BugLogger.info(TAG, "MediaStore returned ${videos.size} video(s)")

            if (videos.isEmpty()) {
                BugLogger.warn(TAG, "No videos found — device may have no media or permission was revoked")
            }

            var inserted = 0
            var errors   = 0
            videos.forEach { v ->
                try {
                    dao.insert(v)
                    inserted++
                    BugLogger.debug(TAG, "Inserted: ${v.name} (${v.path})")
                } catch (e: Exception) {
                    errors++
                    BugLogger.error(TAG, "Failed to insert ${v.path}", e)
                }
            }

            val total   = dao.getVideoCount().first()
            val elapsed = System.currentTimeMillis() - startMs
            BugLogger.info(TAG, "Scan complete — inserted=$inserted errors=$errors total_in_db=$total elapsed=${elapsed}ms")

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
