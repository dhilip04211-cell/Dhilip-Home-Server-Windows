package com.example.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

enum class ServerConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    OFFLINE,
    ERROR
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0,
    val serverVersion: String? = null,
    val serverName: String? = null,
    val message: String
)

object ServerConfig {
    const val DEFAULT_PORT = 8080
    private const val PREFS_NAME = "dhiliphome_server_config"
    private const val KEY_SERVER_HOST = "server_host"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_USE_HTTPS = "use_https"

    var connectionTimeoutSec: Long = 5L
    var readTimeoutSec: Long = 15L
    var writeTimeoutSec: Long = 15L

    var appContext: Context? = null
        private set

    private var sharedPreferences: SharedPreferences? = null

    private val _serverHost = MutableStateFlow("")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _serverPort = MutableStateFlow(DEFAULT_PORT)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _useHttps = MutableStateFlow(false)
    val useHttps: StateFlow<Boolean> = _useHttps.asStateFlow()

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    // Backward-compatible alias for existing views
    val serverUrl: StateFlow<String> get() = _baseUrl

    private val _connectionState = MutableStateFlow(ServerConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ServerConnectionState> = _connectionState.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences = prefs
        val savedHost = prefs.getString(KEY_SERVER_HOST, "") ?: ""
        val savedPort = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        val savedHttps = prefs.getBoolean(KEY_USE_HTTPS, false)

        _serverHost.value = savedHost
        _serverPort.value = savedPort
        _useHttps.value = savedHttps
        updateBaseUrl()
    }

    fun isConfigured(): Boolean = _serverHost.value.isNotBlank()

    fun setServer(host: String, port: Int = DEFAULT_PORT, useHttps: Boolean = false) {
        val cleanHost = cleanHostname(host)
        _serverHost.value = cleanHost
        _serverPort.value = if (port in 1..65535) port else DEFAULT_PORT
        _useHttps.value = useHttps
        updateBaseUrl()
        persist()
    }

    /**
     * Parses user inputs like:
     * - "192.168.1.100" -> host: 192.168.1.100, port: 8080
     * - "192.168.1.100:8080" -> host: 192.168.1.100, port: 8080
     * - "http://192.168.1.100:8080" -> host: 192.168.1.100, port: 8080
     * - "https://server.lan:9443" -> host: server.lan, port: 9443, useHttps: true
     */
    fun parseAndSetServerAddress(input: String, defaultPort: Int = DEFAULT_PORT) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            _serverHost.value = ""
            _serverPort.value = defaultPort
            updateBaseUrl()
            persist()
            return
        }

        var scheme = if (trimmed.startsWith("https://", ignoreCase = true)) "https" else "http"
        var withoutScheme = trimmed
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("HTTP://")
            .removePrefix("HTTPS://")
            .trimEnd('/')

        val colonIndex = withoutScheme.indexOf(':')
        val hostPart: String
        val portPart: Int

        if (colonIndex != -1) {
            hostPart = withoutScheme.substring(0, colonIndex).trim()
            val parsedPort = withoutScheme.substring(colonIndex + 1).takeWhile { it.isDigit() }.toIntOrNull()
            portPart = parsedPort ?: defaultPort
        } else {
            hostPart = withoutScheme.trim()
            portPart = defaultPort
        }

        val cleanHost = cleanHostname(hostPart)
        _serverHost.value = cleanHost
        _serverPort.value = portPart
        _useHttps.value = scheme == "https"
        updateBaseUrl()
        persist()
    }

    fun updateServerUrl(url: String) {
        parseAndSetServerAddress(url)
    }

    fun setConnectionState(state: ServerConnectionState) {
        _connectionState.value = state
    }

    private fun cleanHostname(raw: String): String {
        return raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("//")
            .split("/")
            .firstOrNull() ?: ""
    }

    private fun updateBaseUrl() {
        val host = _serverHost.value.trim()
        if (host.isEmpty()) {
            _baseUrl.value = ""
            return
        }
        val scheme = if (_useHttps.value) "https" else "http"
        val port = _serverPort.value
        _baseUrl.value = "$scheme://$host:$port"
    }

    private fun persist() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_SERVER_HOST, _serverHost.value)
            putInt(KEY_SERVER_PORT, _serverPort.value)
            putBoolean(KEY_USE_HTTPS, _useHttps.value)
            apply()
        }
    }

    suspend fun testConnection(
        targetHost: String = _serverHost.value,
        targetPort: Int = _serverPort.value,
        useHttps: Boolean = _useHttps.value
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanHost = cleanHostname(targetHost)
        if (cleanHost.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "Please enter a valid server IP address or hostname."
            )
        }

        val scheme = if (useHttps) "https" else "http"
        val testBaseUrl = "$scheme://$cleanHost:$targetPort"
        val healthUrl = "$testBaseUrl/api/health"

        val client = OkHttpClient.Builder()
            .connectTimeout(connectionTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(healthUrl)
                .header("User-Agent", AuthInterceptor.USER_AGENT)
                .header("Accept", "application/json, */*")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val bodyString = response.body?.string() ?: ""
            response.close()

            if (statusCode in 200..299) {
                var version = "0.1.0"
                var serverName = "DhilipHome Server"
                try {
                    val json = org.json.JSONObject(bodyString)
                    if (json.has("version")) version = json.getString("version")
                    if (json.has("server")) serverName = json.getString("server")
                } catch (_: Exception) {}

                _connectionState.value = ServerConnectionState.CONNECTED

                ConnectionTestResult(
                    isSuccess = true,
                    statusCode = statusCode,
                    latencyMs = latency,
                    serverVersion = version,
                    serverName = serverName,
                    message = "Server Online • Version: $version (${latency}ms)"
                )
            } else {
                _connectionState.value = ServerConnectionState.ERROR
                ConnectionTestResult(
                    isSuccess = false,
                    statusCode = statusCode,
                    latencyMs = latency,
                    message = "Server responded with HTTP $statusCode. Verify dhilip-home-server is running on port $targetPort."
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            _connectionState.value = ServerConnectionState.OFFLINE
            val errorMsg = when {
                e is java.net.ConnectException -> "Unable to connect to server ($cleanHost:$targetPort). Check if server is running and device is on the same local Wi-Fi."
                e is java.net.SocketTimeoutException -> "Connection timed out reaching $cleanHost:$targetPort. Check server firewall."
                e is java.net.UnknownHostException -> "Host '$cleanHost' could not be resolved. Please check the IP address."
                else -> "Unable to connect to server: ${e.localizedMessage ?: "Unknown error"}"
            }
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                latencyMs = latency,
                message = errorMsg
            )
        }
    }
}
