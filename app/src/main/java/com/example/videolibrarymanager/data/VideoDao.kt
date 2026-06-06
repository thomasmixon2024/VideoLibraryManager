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
}
