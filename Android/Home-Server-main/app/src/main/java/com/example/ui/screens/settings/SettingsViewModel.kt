package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.HttpServerRepository
import com.example.network.ApiClient
import com.example.network.ConnectionTestResult
import com.example.network.DiscoveredServer
import com.example.network.NetworkResult
import com.example.network.ServerConfig
import com.example.network.ServerConnectionManager
import com.example.network.ServerConnectionState
import com.example.network.models.AuthLoginRequest
import com.example.network.models.AuthResponse
import com.example.network.models.AuthStatusResponse
import com.example.network.models.ServerInfoResponse
import com.example.ui.theme.ThemeController
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    val serverHost: StateFlow<String> = ServerConfig.serverHost
    val serverPort: StateFlow<Int> = ServerConfig.serverPort
    val serverAddress: StateFlow<String> = ServerConfig.baseUrl
    val useHttps: StateFlow<Boolean> = ServerConfig.useHttps
    val connectionState: StateFlow<ServerConnectionState> = ServerConfig.connectionState

    val isConnected: StateFlow<Boolean> = ServerConnectionManager.isConnected
    val serverVersion: StateFlow<String?> = ServerConnectionManager.serverVersion
    val serverName: StateFlow<String?> = ServerConnectionManager.serverName
    val serverInfo: StateFlow<ServerInfoResponse?> = ServerConnectionManager.serverInfo
    val isDiscovering: StateFlow<Boolean> = ServerConnectionManager.isDiscovering

    private val _discoveredServer = MutableStateFlow<DiscoveredServer?>(null)
    val discoveredServer: StateFlow<DiscoveredServer?> = _discoveredServer.asStateFlow()

    private val _authStatus = MutableStateFlow<AuthStatusResponse?>(null)
    val authStatus: StateFlow<AuthStatusResponse?> = _authStatus.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _autoLockTime = MutableStateFlow("After 5 minutes")
    val autoLockTime: StateFlow<String> = _autoLockTime.asStateFlow()

    val currentThemeMode: ThemeMode
        get() = ThemeController.currentThemeMode

    // Diagnostic properties
    val isDebugMode: StateFlow<Boolean> = com.example.network.ApiDiagnosticManager.isDebugMode
    val diagnosticHistory: StateFlow<List<com.example.network.ApiTransaction>> = com.example.network.ApiDiagnosticManager.history
    val lastEndpoint: StateFlow<String> = com.example.network.ApiDiagnosticManager.lastEndpoint
    val lastHttpStatus: StateFlow<Int?> = com.example.network.ApiDiagnosticManager.lastHttpStatus
    val lastResponseBody: StateFlow<String> = com.example.network.ApiDiagnosticManager.lastResponseBody

    init {
        viewModelScope.launch {
            checkAuthStatus()
        }
    }

    fun setDebugMode(enabled: Boolean) {
        com.example.network.ApiDiagnosticManager.setDebugMode(enabled)
    }

    fun clearDiagnosticHistory() {
        com.example.network.ApiDiagnosticManager.clearHistory()
    }

    suspend fun runDiagnosticSuite(): List<Pair<String, Int>> {
        return com.example.network.ApiDiagnosticManager.runFullDiagnosticSuite()
    }

    fun setServerAddress(address: String) {
        ServerConfig.parseAndSetServerAddress(address)
        viewModelScope.launch {
            HttpServerRepository.refreshServerStatus()
        }
    }

    fun setServerConfig(host: String, port: Int, https: Boolean) {
        ServerConfig.setServer(host, port, https)
        viewModelScope.launch {
            HttpServerRepository.refreshServerStatus()
        }
    }

    suspend fun testConnection(targetAddress: String): ConnectionTestResult {
        return ServerConfig.testConnection(targetHost = targetAddress)
    }

    suspend fun testConnection(host: String, port: Int, https: Boolean): ConnectionTestResult {
        return ServerConfig.testConnection(targetHost = host, targetPort = port, useHttps = https)
    }

    fun discoverServer() {
        viewModelScope.launch {
            val server = ServerConnectionManager.discoverServer()
            _discoveredServer.value = server
            if (server != null) {
                setServerConfig(server.ip, server.port, false)
            }
        }
    }

    suspend fun checkAuthStatus() {
        val api = ApiClient.getApiService() ?: return
        try {
            val res = api.getAuthStatus()
            if (res.isSuccessful) {
                _authStatus.value = res.body()
            }
        } catch (_: Exception) {}
    }

    suspend fun login(username: String, password: String): NetworkResult<AuthResponse> {
        val api = ApiClient.getApiService() ?: return NetworkResult.Error(
            code = NetworkResult.CODE_SERVER_UNREACHABLE,
            message = "Server is not configured or offline"
        )
        return try {
            val res = api.login(AuthLoginRequest(username = username, password = password))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                if (body.token != null) {
                    ApiClient.authInterceptor.setToken(body.token)
                }
                checkAuthStatus()
                // Login unlocks protected dashboard/file endpoints; refresh all server-backed data now.
                HttpServerRepository.refreshServerStatus()
                NetworkResult.Success(body)
            } else {
                NetworkResult.parseServerError(res.errorBody()?.string(), res.code())
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    fun logout() {
        ApiClient.authInterceptor.clear()
        _authStatus.value = AuthStatusResponse(authenticated = false, rootUsername = null)
        viewModelScope.launch {
            try {
                ApiClient.getApiService()?.logout()
            } catch (_: Exception) {}
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        ThemeController.currentThemeMode = mode
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
    }

    fun setAutoLockTime(time: String) {
        _autoLockTime.value = time
    }
}
