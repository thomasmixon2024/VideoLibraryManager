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
    fun searchVideos(query: String): Flow<List<VideoEntity>> = videoDao.searchVideos(query)

    suspend fun insert(video: VideoEntity)              = videoDao.insert(video)
    suspend fun insertAll(videos: List<VideoEntity>)    = videoDao.insertAll(videos)
    suspend fun deleteByPath(path: String)              = videoDao.deleteByPath(path)
    suspend fun clearAllVideos()                        = videoDao.clearAllVideos()
}
