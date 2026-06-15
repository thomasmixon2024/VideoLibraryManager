package com.example.videolibrarymanager.scanner

import android.content.Context
import android.provider.MediaStore
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.BugLogger
import com.example.videolibrarymanager.util.VideoMetadataHelper

/**
 * VideoScanner — queries MediaStore for all video files on the device.
 * Optionally filters to a specific set of MediaStore bucket (folder) names.
 */
class VideoScanner(private val context: Context) {

    /**
     * Returns video files found via MediaStore, optionally limited to [includedFolders].
     * [includedFolders] — set of BUCKET_DISPLAY_NAME strings to allow.
     *                      Empty set means "include all folders" (default, open filter).
     * Requires READ_MEDIA_VIDEO (API 33+) or READ_EXTERNAL_STORAGE (≤ API 32).
     */
    suspend fun scanAll(
        limit: Int = 500,
        includedFolders: Set<String> = emptySet()
    ): List<VideoEntity> {
        val folderDesc = if (includedFolders.isEmpty()) "all" else includedFolders.joinToString()
        BugLogger.debug(TAG, "scanAll() — limit=$limit folders=$folderDesc")
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
                val idxDate     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val idxBucket   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                var skipped = 0
                while (cursor.moveToNext() && results.size < limit) {
                    val path = cursor.getString(idxData)
                    if (path.isNullOrBlank()) {
                        skipped++
                        BugLogger.warn(TAG, "Row skipped — DATA column is null/blank (row ${cursor.position})")
                        continue
                    }

                    val name   = cursor.getString(idxName) ?: "Unknown"
                    val bucket = cursor.getString(idxBucket) ?: "Uncategorized"

                    // Apply folder filter — skip if bucket not in the allowed set
                    if (includedFolders.isNotEmpty() && bucket !in includedFolders) {
                        skipped++
                        continue
                    }

                    val date   = cursor.getLong(idxDate) * 1000L

                    // Extract precise metadata natively. Do not compute checksum on every scan,
                    // because it is expensive and not currently used by the app's workflows.
                    val metadata = VideoMetadataHelper.processVideo(
                        context,
                        path,
                        calculateChecksum = false
                    )
                    
                    if (metadata != null) {
                        results += VideoEntity(
                            name       = name,
                            path       = path,
                            category   = bucket,
                            duration   = metadata.durationMs,
                            size       = java.io.File(path).length(),
                            resolution = if (metadata.width > 0 && metadata.height > 0) "${metadata.width}x${metadata.height}" else "",
                            dateAdded  = date,
                            thumbnailPath = metadata.thumbnailPath,
                            checksum   = metadata.checksum
                        )
                    } else {
                        // Mark as corrupt if we couldn't process it
                        results += VideoEntity(
                            name       = name,
                            path       = path,
                            category   = bucket,
                            isCorrupt  = true,
                            errorMessage = "Failed to process native metadata"
                        )
                    }
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
