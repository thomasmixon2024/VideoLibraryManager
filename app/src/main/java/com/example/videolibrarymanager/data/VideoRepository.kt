package com.example.videolibrarymanager.data

import kotlinx.coroutines.flow.Flow

// Removed @Inject — no DI framework (Hilt/Dagger) is configured in this project.
// Pass VideoDao manually via constructor.
class VideoRepository(
    private val videoDao: VideoDao
) {
    fun getAllVideos(): Flow<List<VideoEntity>>          = videoDao.getAllVideos()
    fun getQuarantinedVideos(): Flow<List<VideoEntity>> = videoDao.getQuarantinedVideos()
    fun getVideoCount()                                 = videoDao.getVideoCount()
    fun searchVideos(query: String): Flow<List<VideoEntity>> {
        // FTS4/5 MATCH requires wildcard (*) for prefix matching.
        // Strip characters that are special in FTS syntax to avoid malformed query errors,
        // then append * to every whitespace-delimited token so "mov" matches "movie.mp4".
        val ftsQuery = query
            .trim()
            .replace(Regex("""["\-*]"""), "")   // remove FTS-special chars from user input
            .split(Regex("\\s+"))               // split on any whitespace
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }       // e.g. "big buck" → "big* buck*"
        return if (ftsQuery.isBlank()) videoDao.getAllVideos() else videoDao.searchVideos(ftsQuery)
    }

    suspend fun insert(video: VideoEntity)              = videoDao.insert(video)
    suspend fun insertAll(videos: List<VideoEntity>)    = videoDao.insertAll(videos)
    suspend fun deleteByPath(path: String)              = videoDao.deleteByPath(path)
    suspend fun clearAllVideos()                        = videoDao.clearAllVideos()
}
