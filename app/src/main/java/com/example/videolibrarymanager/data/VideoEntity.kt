package com.example.videolibrarymanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["category", "dateAdded"]),
        Index(value = ["isCorrupt"]),
        Index(value = ["name"])
    ]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val category: String = "Uncategorized",
    val duration: Long = 0,
    val resolution: String = "",
    val size: Long = 0,
    val thumbnailPath: String? = null,
    val checksum: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCorrupt: Boolean = false,
    val errorMessage: String? = null
)
