package com.example.videolibrarymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isCorrupt = 1")
    fun getQuarantinedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT COUNT(*) FROM videos")
    fun getVideoCount(): Flow<Int>

    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN videos_fts ON videos_fts.docid = v.id
        WHERE videos_fts MATCH :query
        ORDER BY v.dateAdded DESC
        LIMIT 50
    """)
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM videos")
    suspend fun clearAllVideos()

    @Query("UPDATE videos SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)
}
