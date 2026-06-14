package com.example.videolibrarymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videolibrarymanager.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val autoScanEnabled: StateFlow<Boolean> = repository.autoScanEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val skipCorruptVideos: StateFlow<Boolean> = repository.skipCorruptVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanLimit: StateFlow<Float> = repository.scanLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50f)

    /** Empty set means "all folders" (no filter active). */
    val includedFolders: StateFlow<Set<String>> = repository.includedFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveAutoScanEnabled(enabled)
        }
    }

    fun setSkipCorruptVideos(skip: Boolean) {
        viewModelScope.launch {
            repository.saveSkipCorruptVideos(skip)
        }
    }

    fun setScanLimit(limit: Float) {
        viewModelScope.launch {
            repository.saveScanLimit(limit)
        }
    }

    /** Adds the folder if absent, removes it if already present. Empty set = all folders. */
    fun toggleFolder(folder: String) {
        viewModelScope.launch {
            val current = repository.includedFolders.first()
            val updated = if (folder in current) current - folder else current + folder
            repository.saveIncludedFolders(updated)
        }
    }

    /** Clears all folder restrictions — scanner will include every folder again. */
    fun clearFolderFilter() {
        viewModelScope.launch {
            repository.saveIncludedFolders(emptySet())
        }
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
