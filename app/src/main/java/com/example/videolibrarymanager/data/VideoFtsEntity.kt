package com.example.videolibrarymanager.data

import androidx.room.Entity
import androidx.room.Fts4

// Using FTS4
@Entity(tableName = "videos_fts")
@Fts4(contentEntity = VideoEntity::class)
data class VideoFtsEntity(
    val name: String,
    val category: String,
    val path: String
)
