package com.example.videolibrarymanager.data

import androidx.room.Entity
import androidx.room.Fts5

// Using FTS5 to match the database migration in VideoDatabase (MIGRATION_2_3)
@Entity(tableName = "videos_fts")
@Fts5(contentEntity = VideoEntity::class)
data class VideoFtsEntity(
    val name: String,
    val category: String,
    val path: String
)
