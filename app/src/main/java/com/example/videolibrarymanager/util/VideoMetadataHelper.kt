package com.example.videolibrarymanager.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnailPath: String?,
    val checksum: String?
)

object VideoMetadataHelper {

    /**
     * Extracts metadata, generates a thumbnail, and calculates a SHA-256 checksum for the given video file.
     * All disk/IO operations are run on the IO dispatcher.
     */
    suspend fun processVideo(context: Context, videoPath: String): VideoMetadata? = withContext(Dispatchers.IO) {
        val file = File(videoPath)
        if (!file.exists() || !file.canRead()) {
            BugLogger.warn("VideoMetadataHelper", "File does not exist or cannot be read: $videoPath")
            return@withContext null
        }

        var duration = 0L
        var width = 0
        var height = 0
        var thumbnailPath: String? = null
        val checksum: String?

        // 1. Calculate Checksum
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            checksum = digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            BugLogger.error("VideoMetadataHelper", "Failed to calculate checksum for $videoPath", e)
            return@withContext null
        }

        // 2. Extract Metadata & Thumbnail
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            // Generate thumbnail from 1st second (or 0 if shorter)
            val timeUs = if (duration > 1000) 1000000L else 0L
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            
            if (frame != null) {
                val thumbFile = File(context.cacheDir, "thumb_$checksum.jpg")
                FileOutputStream(thumbFile).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                thumbnailPath = thumbFile.absolutePath
            }
        } catch (e: Exception) {
            BugLogger.error("VideoMetadataHelper", "Failed to extract metadata from $videoPath", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }

        VideoMetadata(
            durationMs = duration,
            width = width,
            height = height,
            thumbnailPath = thumbnailPath,
            checksum = checksum
        )
    }
}
