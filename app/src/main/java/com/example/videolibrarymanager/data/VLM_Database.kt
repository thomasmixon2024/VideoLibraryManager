package com.example.videolibrarymanager.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VideoEntity::class], version = 1, exportSchema = false)
abstract class VLM_Database : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
