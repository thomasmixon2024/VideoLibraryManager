package com.example.videolibrarymanager.scanner

import android.content.Context
import android.provider.MediaStore
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.BugLogger
import com.example.videolibrarymanager.util.VideoMetadataHelper
import java.io.File

/**
 * VideoScanner — two-pass scanner:
 * Pass 1: MediaStore query (fast, covers most indexed files)
 * Pass 2: File.walk() over all storage roots (catches unindexed files)
 * Both passes deduplicate by absolute path.
 */
class VideoScanner(private val context: Context) {

    companion object {
        private const val TAG = "VideoScanner"

        /** Every video container/codec extension we recognize. */
        val VIDEO_EXTENSIONS = setOf(
            // Common
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            // Mobile / streaming
            "3gp", "3g2", "ts", "mts", "m2ts",
            // Less common but valid
            "vob", "ogv", "ogm", "divx", "xvid", "rmvb", "rm",
            "asf", "mpg", "mpeg", "mp2", "mpe", "mpv", "m2v",
            "f4v", "f4p", "f4a", "f4b",
            // Raw / professional
            "mxf", "dv", "mod", "tod", "trp",
            // Apple
            "qt",
            // Android screen record / misc
            "amv", "nsv", "roq", "yuv"
        )

        /** Storage roots to walk in pass 2. */
        private val STORAGE_ROOTS = listOf(
            "/sdcard",
            "/storage/emulated/0",
            "/storage/self/primary"
        )
    }

    suspend fun scanAll(
        limit: Int = 5000,
        includedFolders: Set<String> = emptySet()
    ): List<VideoEntity> {
        BugLogger.info(TAG, "scanAll() start — limit=$limit folders=${if (includedFolders.isEmpty()) "ALL" else includedFolders}")

        val seen    = mutableSetOf<String>()   // dedup by path
        val results = mutableListOf<VideoEntity>()

        // ── Pass 1: MediaStore ────────────────────────────────────────────
        BugLogger.info(TAG, "Pass 1: MediaStore query")
        mediaStoreScan(limit, includedFolders, seen, results)
        BugLogger.info(TAG, "Pass 1 complete — ${results.size} found")

        // ── Pass 2: File.walk() fallback ──────────────────────────────────
        BugLogger.info(TAG, "Pass 2: File.walk() over storage roots")
        fileWalkScan(limit, includedFolders, seen, results)
        BugLogger.info(TAG, "Pass 2 complete — ${results.size} total found")

        return results
    }

    // ── Pass 1: MediaStore ────────────────────────────────────────────────

    private suspend fun mediaStoreScan(
        limit: Int,
        includedFolders: Set<String>,
        seen: MutableSet<String>,
        results: MutableList<VideoEntity>
    ) {
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
                projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idxName   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val idxData   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val idxDate   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val idxBucket = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                var skipped   = 0

                while (cursor.moveToNext() && results.size < limit) {
                    val path = cursor.getString(idxData) ?: run { skipped++; continue }
                    if (path.isBlank() || path in seen) { skipped++; continue }

                    val bucket = cursor.getString(idxBucket) ?: "Uncategorized"
                    if (includedFolders.isNotEmpty() && bucket !in includedFolders) { skipped++; continue }

                    seen += path
                    val date = cursor.getLong(idxDate) * 1000L
                    val name = cursor.getString(idxName) ?: File(path).name

                    results += buildEntity(name, path, bucket, date)
                }
                BugLogger.info(TAG, "MediaStore: ${results.size} added, $skipped skipped")
            } ?: BugLogger.warn(TAG, "MediaStore query returned null cursor")
        } catch (e: SecurityException) {
            BugLogger.error(TAG, "SecurityException in MediaStore scan", e)
        } catch (e: Exception) {
            BugLogger.error(TAG, "Error in MediaStore scan", e)
        }
    }

    // ── Pass 2: File.walk() ───────────────────────────────────────────────

    private suspend fun fileWalkScan(
        limit: Int,
        includedFolders: Set<String>,
        seen: MutableSet<String>,
        results: MutableList<VideoEntity>
    ) {
        val roots = STORAGE_ROOTS.map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .distinctBy { it.canonicalPath }

        BugLogger.info(TAG, "File.walk roots: ${roots.map { it.path }}")

        for (root in roots) {
            if (results.size >= limit) break
            try {
                root.walkTopDown()
                    .onEnter { dir ->
                        // Skip hidden dirs and known junk paths
                        !dir.name.startsWith(".") &&
                        dir.name != "Android" &&
                        dir.name != "data" &&
                        dir.name != "obb"
                    }
                    .filter { it.isFile }
                    .filter { it.extension.lowercase() in VIDEO_EXTENSIONS }
                    .forEach { file ->
                        if (results.size >= limit) return@forEach
                        val path = file.absolutePath
                        if (path in seen) return@forEach
                        seen += path

                        val bucket = file.parentFile?.name ?: "Uncategorized"
                        if (includedFolders.isNotEmpty() && bucket !in includedFolders) return@forEach

                        val date = file.lastModified()
                        results += buildEntity(file.name, path, bucket, date)
                        BugLogger.debug(TAG, "File.walk found: $path")
                    }
            } catch (e: SecurityException) {
                BugLogger.warn(TAG, "SecurityException walking ${root.path}: ${e.message}")
            } catch (e: Exception) {
                BugLogger.error(TAG, "Error walking ${root.path}", e)
            }
        }
    }

    // ── Shared entity builder ─────────────────────────────────────────────

    private suspend fun buildEntity(
        name: String,
        path: String,
        bucket: String,
        date: Long
    ): VideoEntity {
        val metadata = VideoMetadataHelper.processVideo(
            context, path, calculateChecksum = false
        )
        return if (metadata != null) {
            VideoEntity(
                name          = name,
                path          = path,
                category      = bucket,
                duration      = metadata.durationMs,
                size          = File(path).length(),
                resolution    = if (metadata.width > 0 && metadata.height > 0)
                                    "${metadata.width}x${metadata.height}" else "",
                dateAdded     = date,
                thumbnailPath = metadata.thumbnailPath,
                checksum      = metadata.checksum
            )
        } else {
            VideoEntity(
                name         = name,
                path         = path,
                category     = bucket,
                dateAdded    = date,
                size         = File(path).length(),
                isCorrupt    = true,
                errorMessage = "Metadata extraction failed"
            )
        }
    }
}
