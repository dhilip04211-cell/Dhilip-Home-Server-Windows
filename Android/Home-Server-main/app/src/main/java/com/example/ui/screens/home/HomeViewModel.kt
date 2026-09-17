package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.model.RecentActivity
import com.example.data.model.ServerStatus
import com.example.data.model.StorageInfo
import com.example.data.repository.HttpServerRepository
import com.example.domain.repository.ServerRepository
import com.example.network.ServerConnectionManager
import com.example.network.models.ServerInfoResponse
import com.example.network.models.SystemInfoResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ServerRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val systemInfo: StateFlow<SystemInfoResponse?> = ServerConnectionManager.systemInfo
    val serverInfo: StateFlow<ServerInfoResponse?> = ServerConnectionManager.serverInfo

    val serverStatus: StateFlow<ServerStatus> = repository.getServerStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerStatus()
        )

    val continueWatching: StateFlow<List<MediaItem>> = repository.getContinueWatching()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentActivities: StateFlow<List<RecentActivity>> = repository.getRecentActivities()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val storageInfo: StateFlow<StorageInfo> = repository.getStorageInfo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StorageInfo()
        )

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                HttpServerRepository.refreshServerStatus()
                HttpServerRepository.refreshStorageInfo()
                ServerConnectionManager.fetchSystemInfo()
                ServerConnectionManager.fetchServerInfo()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
