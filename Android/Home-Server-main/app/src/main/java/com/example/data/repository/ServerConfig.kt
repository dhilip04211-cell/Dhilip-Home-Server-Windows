package com.example.data.repository

import com.example.network.ConnectionTestResult
import com.example.network.ServerConfig as NetworkServerConfig
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

typealias ConnectionTestResult = com.example.network.ConnectionTestResult

/**
 * Backward compatibility facade delegating to centralized com.example.network.ServerConfig
 */
object ServerConfig {
    val serverUrl: StateFlow<String> get() = NetworkServerConfig.baseUrl
    val serverHost: StateFlow<String> get() = NetworkServerConfig.serverHost
    val serverPort: StateFlow<Int> get() = NetworkServerConfig.serverPort

    fun getClient(): OkHttpClient = com.example.network.ApiClient.okHttpClient

    fun updateServerUrl(url: String) {
        NetworkServerConfig.parseAndSetServerAddress(url)
    }

    suspend fun testConnection(targetUrl: String = NetworkServerConfig.baseUrl.value): ConnectionTestResult {
        return NetworkServerConfig.testConnection(targetHost = targetUrl)
    }
}
