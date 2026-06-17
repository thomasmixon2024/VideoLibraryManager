package com.example.videolibrarymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val filePath: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val thumbnailPath: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int
)
