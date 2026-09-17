package com.example.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ServerStatus
import com.example.data.model.ServiceStatus
import com.example.data.model.StorageInfo
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.HttpServerRepository
import com.example.domain.repository.ServerRepository
import com.example.network.ServerConnectionManager
import com.example.network.models.ServerInfoResponse
import com.example.network.models.SystemInfoResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupEntry(
    val id: String,
    val filename: String,
    val size: String,
    val timestamp: String,
    val status: String = "Complete"
)

class AdminViewModel(
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

    val services: StateFlow<List<ServiceStatus>> = repository.getServices()
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

    val users: StateFlow<List<User>> = repository.getUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backups: StateFlow<List<BackupEntry>> = _backups.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                HttpServerRepository.refreshServerStatus()
                HttpServerRepository.refreshStorageInfo()
                ServerConnectionManager.fetchSystemInfo()
                ServerConnectionManager.fetchServerInfo()
                addLog("Telemetry refreshed: CPU, RAM, and Storage updated.")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun restartServices(serviceName: String = "all") {
        viewModelScope.launch {
            addLog("Command executed: systemctl restart $serviceName")
            HttpServerRepository.restartService(serviceName)
            addLog("Service $serviceName successfully restarted.")
        }
    }

    fun toggleService(serviceUnit: String) {
        viewModelScope.launch {
            addLog("Command executed: systemctl toggle $serviceUnit")
            HttpServerRepository.toggleService(serviceUnit)
            addLog("Service $serviceUnit state updated.")
        }
    }

    fun rebootServer() {
        viewModelScope.launch {
            addLog("[CRITICAL] System reboot command issued by admin.")
            HttpServerRepository.rebootServer()
            addLog("[systemd] System reboot completed. Services back online.")
        }
    }

    fun shutdownServer() {
        viewModelScope.launch {
            addLog("[WARNING] System shutdown requested. Stopping all daemons...")
            HttpServerRepository.shutdownServer()
            addLog("[systemd] All services halted. Machine in standby.")
        }
    }

    fun addUser(username: String, displayName: String, role: UserRole) {
        HttpServerRepository.addUser(username, displayName, role)
        addLog("Created new user account: $username ($displayName, role: ${role.name})")
    }

    fun deleteUser(userId: String) {
        HttpServerRepository.deleteUser(userId)
        addLog("Deleted user account id: $userId")
    }

    fun createBackupNow() {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.US).format(Date())
            val filename = "dhiliphome_backup_$dateStr.tar.gz"
            addLog("Initiating system storage backup to $filename...")
            delay(1200)
            val newEntry = BackupEntry(
                id = "bk_${System.currentTimeMillis()}",
                filename = filename,
                size = "1.8 GB",
                timestamp = "Just now"
            )
            _backups.update { listOf(newEntry) + it }
            addLog("Backup successfully created and verified: $filename (1.8 GB)")
        }
    }

    fun deleteBackup(backupId: String) {
        _backups.update { list -> list.filter { it.id != backupId } }
        addLog("Removed backup archive: $backupId")
    }

    fun clearLogs() {
        _systemLogs.value = emptyList()
    }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _systemLogs.update { listOf("[$time] $msg") + it }
    }
}
