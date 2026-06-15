package com.example.videolibrarymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.util.BugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

class VideoViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val videos: StateFlow<List<VideoEntity>> = repository.getAllVideos()
        .onEach { list ->
            BugLogger.debug(TAG, "Flow emission: ${list.size} video(s)")
            _isLoading.value = false
        }
        .catch { e ->
            BugLogger.error(TAG, "Error in getAllVideos flow", e)
            _error.value = e.message
            _isLoading.value = false
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<VideoEntity>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllVideos()
            else repository.searchVideos(query)
        }
        .catch { e ->
            BugLogger.error(TAG, "Error in searchResults flow", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        BugLogger.debug(TAG, "init — starting video observation")
        observeCount()
    }

    private fun observeCount() {
        viewModelScope.launch {
            repository.getVideoCount().collect { count ->
                BugLogger.debug(TAG, "Video count updated: $count")
                _videoCount.value = count
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun retry() {
        BugLogger.info(TAG, "retry() called by user")
        _error.value = null
        _isLoading.value = true
    }

    override fun onCleared() {
        super.onCleared()
        BugLogger.debug(TAG, "onCleared")
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

    fun updateVideoCategory(id: Long, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BugLogger.info(TAG, "updateVideoCategory() — updating id=$id to category=$category")
                repository.updateCategory(id, category)
                BugLogger.info(TAG, "updateVideoCategory() — category updated successfully")
            } catch (e: Exception) {
                BugLogger.error(TAG, "updateVideoCategory() failed", e)
                _error.value = e.message
            }
        }
    }

    companion object { private const val TAG = "VideoViewModel" }
}

