package com.example.videolibrarymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.util.BugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _videos    = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videos: StateFlow<List<VideoEntity>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()

    init {
        BugLogger.debug(TAG, "init — starting video observation")
        loadVideos()
        observeCount()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            BugLogger.info(TAG, "loadVideos() — collecting getAllVideos flow")
            try {
                repository.getAllVideos()
                    .onEach { list ->
                        BugLogger.debug(TAG, "Flow emission: ${list.size} video(s)")
                        _isLoading.value = false
                    }
                    .catch { e ->
                        BugLogger.error(TAG, "Error in getAllVideos flow", e)
                        _error.value     = e.message
                        _isLoading.value = false
                    }
                    .collect { list -> _videos.value = list }
            } catch (e: Exception) {
                BugLogger.error(TAG, "loadVideos() threw", e)
                _error.value     = e.message
                _isLoading.value = false
            }
        }
    }

    private fun observeCount() {
        viewModelScope.launch {
            repository.getVideoCount().collect { count ->
                BugLogger.debug(TAG, "Video count updated: $count")
                _videoCount.value = count
            }
        }
    }

    fun retry() {
        BugLogger.info(TAG, "retry() called by user")
        loadVideos()
    }

    override fun onCleared() {
        super.onCleared()
        BugLogger.debug(TAG, "onCleared")
    }

    fun searchVideos(query: String): Flow<List<VideoEntity>> {
        return repository.searchVideos(query)
    }

    fun clearAllVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BugLogger.info(TAG, "clearAllVideos() — wiping video catalog")
                repository.clearAllVideos()
                BugLogger.info(TAG, "clearAllVideos() — catalog cleared successfully")
            } catch (e: Exception) {
                BugLogger.error(TAG, "clearAllVideos() failed", e)
                _error.value = e.message
            }
        }
    }

    companion object { private const val TAG = "VideoViewModel" }
}

