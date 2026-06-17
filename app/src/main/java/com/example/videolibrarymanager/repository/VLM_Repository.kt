package com.example.videolibrarymanager.repository

import android.content.Context
import androidx.room.Room
import com.example.videolibrarymanager.data.VLM_Database
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.util.VLM_MediaScanner
import com.example.videolibrarymanager.util.VLM_Auditor
import kotlinx.coroutines.flow.Flow

class VLM_Repository(private val context: Context) {
    private val auditor = VLM_Auditor(context)
    private val scanner = VLM_MediaScanner(context, auditor)
    private val db = Room.databaseBuilder(context, VLM_Database::class.java, "vlm_db")
        .fallbackToDestructiveMigration()
        .build()

    suspend fun scanAndSave(): List<VideoEntity> {
        auditor.logInfo("Repository: Starting full scan")
        val result = scanner.scanMediaLibrary()
        db.videoDao().insertAll(result.videos)
        return result.videos
    }

    fun getAllVideos(): Flow<List<VideoEntity>> = db.videoDao().getAllVideos()
}
