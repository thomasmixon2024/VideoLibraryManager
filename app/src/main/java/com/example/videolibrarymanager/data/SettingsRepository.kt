package com.example.videolibrarymanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(private val context: Context) {

    companion object {
        val AUTO_SCAN_ENABLED   = booleanPreferencesKey("auto_scan_enabled")
        val SKIP_CORRUPT_VIDEOS = booleanPreferencesKey("skip_corrupt_videos")
        val SCAN_LIMIT          = floatPreferencesKey("scan_limit")
        /** Empty set = scan ALL folders (default open filter). */
        val INCLUDED_FOLDERS    = stringSetPreferencesKey("included_folders")

        @Volatile private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    val autoScanEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_SCAN_ENABLED] ?: true
        }

    val skipCorruptVideos: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SKIP_CORRUPT_VIDEOS] ?: false
        }

    val scanLimit: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[SCAN_LIMIT] ?: 50f
        }

    /** Emits the set of allowed folder (bucket) names. Empty = all folders. */
    val includedFolders: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[INCLUDED_FOLDERS] ?: emptySet()
        }

    suspend fun saveAutoScanEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SCAN_ENABLED] = enabled
        }
    }

    suspend fun saveSkipCorruptVideos(skip: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_CORRUPT_VIDEOS] = skip
        }
    }

    suspend fun saveScanLimit(limit: Float) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_LIMIT] = limit
        }
    }

    /** Persists the set of allowed bucket names. Pass emptySet() to scan all. */
    suspend fun saveIncludedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[INCLUDED_FOLDERS] = folders
        }
    }
}
