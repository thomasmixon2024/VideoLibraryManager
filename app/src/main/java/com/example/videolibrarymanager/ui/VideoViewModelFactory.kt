package com.example.videolibrarymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.videolibrarymanager.data.VideoRepository

/**
 * Factory for creating VideoViewModel instances with proper dependency injection.
 * 
 * This factory ensures that VideoRepository is correctly injected into VideoViewModel
 * instead of relying on default reflection-based instantiation which cannot provide
 * the required constructor parameter.
 */
class VideoViewModelFactory(private val repository: VideoRepository) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
            return VideoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
