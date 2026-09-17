package com.example.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.domain.repository.ServerRepository
import com.example.data.repository.HttpServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewModel(
    private val repository: ServerRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(MediaCategory.ALL)
    val selectedCategory: StateFlow<MediaCategory> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshMedia()
    }

    val mediaItems: StateFlow<List<MediaItem>> = _selectedCategory
        .flatMapLatest { category -> repository.getMedia(category) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val continueWatching: StateFlow<List<MediaItem>> = repository.getContinueWatching()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyAdded: StateFlow<List<MediaItem>> = repository.getRecentlyAddedMedia()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favorites: StateFlow<List<MediaItem>> = repository.getFavoriteMedia()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectCategory(category: MediaCategory) {
        _selectedCategory.value = category
        refreshMedia(category)
    }

    fun refreshMedia(category: MediaCategory = _selectedCategory.value) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                HttpServerRepository.refreshMediaCatalog(category)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleFavorite(id: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val isFav = repository.toggleMediaFavorite(id)
            onComplete(isFav)
        }
    }

    fun getMediaById(id: String) = repository.getMediaById(id)

    fun deleteMedia(item: MediaItem, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.deleteFile(item.filePath)
            if (ok) refreshMedia()
            onResult(ok)
        }
    }

    fun renameMedia(item: MediaItem, newName: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val extension = item.filePath.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val name = if (extension != null && !newName.contains('.')) "$newName.$extension" else newName
            val ok = repository.renameFile(item.filePath, name)
            if (ok) refreshMedia()
            onResult(ok)
        }
    }
}
