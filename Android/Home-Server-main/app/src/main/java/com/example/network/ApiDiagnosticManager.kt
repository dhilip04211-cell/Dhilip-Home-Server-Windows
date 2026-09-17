package com.example.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApiTransaction(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val method: String,
    val endpoint: String,
    val fullUrl: String,
    val statusCode: Int,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val responsePreview: String,
    val errorMessage: String? = null
)

object ApiDiagnosticManager {

    private val _isDebugMode = MutableStateFlow(true)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    private val _lastEndpoint = MutableStateFlow("/api/health")
    val lastEndpoint: StateFlow<String> = _lastEndpoint.asStateFlow()

    private val _lastMethod = MutableStateFlow("GET")
    val lastMethod: StateFlow<String> = _lastMethod.asStateFlow()

    private val _lastHttpStatus = MutableStateFlow<Int?>(null)
    val lastHttpStatus: StateFlow<Int?> = _lastHttpStatus.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _lastResponseBody = MutableStateFlow("No requests executed yet.")
    val lastResponseBody: StateFlow<String> = _lastResponseBody.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastRequestTime = MutableStateFlow<String>("-")
    val lastRequestTime: StateFlow<String> = _lastRequestTime.asStateFlow()

    private val _history = MutableStateFlow<List<ApiTransaction>>(emptyList())
    val history: StateFlow<List<ApiTransaction>> = _history.asStateFlow()

    fun setDebugMode(enabled: Boolean) {
        _isDebugMode.value = enabled
    }

    fun recordTransaction(
        method: String,
        endpoint: String,
        fullUrl: String,
        statusCode: Int,
        latencyMs: Long,
        responseBody: String,
        errorMessage: String? = null
    ) {
        val now = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val isSuccess = statusCode in 200..299

        _lastMethod.value = method
        _lastEndpoint.value = endpoint
        _lastHttpStatus.value = statusCode
        _lastLatencyMs.value = latencyMs
        _lastResponseBody.value = responseBody
        _lastError.value = errorMessage
        _lastRequestTime.value = now

        val tx = ApiTransaction(
            timestamp = now,
            method = method,
            endpoint = endpoint,
            fullUrl = fullUrl,
            statusCode = statusCode,
            latencyMs = latencyMs,
            isSuccess = isSuccess,
            responsePreview = if (responseBody.length > 500) responseBody.take(500) + "..." else responseBody,
            errorMessage = errorMessage
        )

        val updated = _history.value.toMutableList()
        updated.add(0, tx)
        if (updated.size > 50) {
            _history.value = updated.subList(0, 50)
        } else {
            _history.value = updated
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _lastResponseBody.value = "History cleared."
        _lastHttpStatus.value = null
        _lastError.value = null
    }

    suspend fun runFullDiagnosticSuite(): List<Pair<String, Int>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Int>>()
        val api = ApiClient.getApiService() ?: return@withContext emptyList()
        val calls = listOf<suspend () -> retrofit2.Response<*>>(
            { api.getHealth() },
            { api.getDiscovery() },
            { api.getServerInfo() },
            { api.getSystemInfo() },
            { api.getStorage() },
            { api.getNetwork() },
            { api.getAuthStatus() },
            { api.getFiles("") },
            { api.getMedia() }
        )
        val names = listOf(
            "/api/health",
            "/api/discovery",
            "/api/server",
            "/api/system",
            "/api/storage",
            "/api/network",
            "/api/auth/status",
            "/api/files",
            "/api/media"
        )
        for (i in calls.indices) {
            try {
                val res = calls[i]()
                results.add(names[i] to res.code())
            } catch (e: Exception) {
                results.add(names[i] to 0)
            }
        }
        results
    }
}

class ApiDiagnosticInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val endpoint = request.url.encodedPath + if (request.url.encodedQuery != null) "?${request.url.encodedQuery}" else ""
        val fullUrl = request.url.toString()
        val method = request.method

        try {
            val response = chain.proceed(request)
            val tookMs = (System.nanoTime() - startNs) / 1_000_000

            var responseBodyString = ""
            try {
                // Peek up to 128KB safely without consuming stream
                val peek = response.peekBody(1024L * 128L)
                responseBodyString = peek.string()
            } catch (e: Exception) {
                responseBodyString = "[Unable to preview body: ${e.message}]"
            }

            ApiDiagnosticManager.recordTransaction(
                method = method,
                endpoint = endpoint,
                fullUrl = fullUrl,
                statusCode = response.code,
                latencyMs = tookMs,
                responseBody = responseBodyString,
                errorMessage = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

            return response
        } catch (e: IOException) {
            val tookMs = (System.nanoTime() - startNs) / 1_000_000
            ApiDiagnosticManager.recordTransaction(
                method = method,
                endpoint = endpoint,
                fullUrl = fullUrl,
                statusCode = 0,
                latencyMs = tookMs,
                responseBody = "Network error: ${e.message}",
                errorMessage = e.message
            )
            throw e
        }
    }
}
