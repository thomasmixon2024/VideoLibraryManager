package com.example.videolibrarymanager.scanner

import android.content.Context
import android.provider.MediaStore
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.BugLogger

/**
 * VideoScanner — queries MediaStore for all video files on the device.
 *
 * TODO: Integrate FFmpeg Kit to extract precise duration, resolution,
 *       codec info, and generate thumbnails for each video.
 * TODO: Add SHA-256 checksum generation for corrupt-file detection.
 */
class VideoScanner(private val context: Context) {

    /**
     * Returns all video files found via MediaStore.
     * Requires READ_MEDIA_VIDEO (API 33+) or READ_EXTERNAL_STORAGE (≤ API 32).
     */
    fun scanAll(): List<VideoEntity> {
        BugLogger.debug(TAG, "scanAll() — querying MediaStore.Video.Media.EXTERNAL_CONTENT_URI")
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
                BugLogger.debug(TAG, "Cursor opened — columnCount=${cursor.columnCount}")

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
                    if (path.isNullOrBlank()) {
                        skipped++
                        BugLogger.warn(TAG, "Row skipped — DATA column is null/blank (row ${cursor.position})")
                        continue
                    }

                    val width  = cursor.getInt(idxWidth)
                    val height = cursor.getInt(idxHeight)
                    val name   = cursor.getString(idxName) ?: "Unknown"
                    val bucket = cursor.getString(idxBucket) ?: "Uncategorized"

                    results += VideoEntity(
                        name       = name,
                        path       = path,
                        category   = bucket,
                        duration   = cursor.getLong(idxDuration),
                        size       = cursor.getLong(idxSize),
                        resolution = if (width > 0 && height > 0) "${width}x${height}" else "",
                        dateAdded  = cursor.getLong(idxDate) * 1000L, // MediaStore gives seconds → ms
                    )
                }

                BugLogger.info(TAG,
                    "Cursor exhausted — found=${results.size} skipped=$skipped"
                )
            } ?: BugLogger.warn(TAG, "ContentResolver.query returned null cursor")
        } catch (e: SecurityException) {
            BugLogger.error(TAG, "SecurityException querying MediaStore — missing permission?", e)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Unexpected error during MediaStore scan", e)
        }

        return results
    }

    companion object {
        private const val TAG = "VideoScanner"
    }
}
