package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    val authInterceptor = AuthInterceptor()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var currentBaseUrl: String = ""
    private var cachedApiService: ApiService? = null
    private var activeWebSocket: WebSocket? = null

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            // Do NOT log auth headers, tokens or credentials
            if (!message.contains("Authorization:", ignoreCase = true) &&
                !message.contains("password", ignoreCase = true) &&
                !message.contains("token", ignoreCase = true)) {
                Log.d(TAG, message)
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(ApiDiagnosticInterceptor())
            .addInterceptor(logging)
            .connectTimeout(ServerConfig.connectionTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(ServerConfig.readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(ServerConfig.writeTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    @Synchronized
    fun getApiService(): ApiService? {
        val baseUrl = ServerConfig.baseUrl.value
        if (baseUrl.isBlank()) return null

        val formattedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (cachedApiService == null || currentBaseUrl != formattedBaseUrl) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(formattedBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()

                cachedApiService = retrofit.create(ApiService::class.java)
                currentBaseUrl = formattedBaseUrl
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Retrofit client: ${e.message}")
                return null
            }
        }
        return cachedApiService
    }

    /**
     * Constructs a safe media streaming URL for ExoPlayer / Media3 / VideoView
     */
    fun getStreamUrl(filePath: String): String {
        val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        val encodedPath = filePath.trimStart('/')
            .split("/")
            .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val token = authInterceptor.getToken()
        return if (!token.isNullOrBlank()) "$baseUrl/api/media/stream/$encodedPath?token=${java.net.URLEncoder.encode(token, "UTF-8")}" else "$baseUrl/api/media/stream/$encodedPath"
    }

    /**
     * Constructs a direct file download URL
     */
    fun getDownloadUrl(filePath: String): String {
        val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        val encodedPath = java.net.URLEncoder.encode(filePath, "UTF-8")
        val token = authInterceptor.getToken()
        return if (!token.isNullOrBlank()) "$baseUrl/api/files/download?path=$encodedPath&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else "$baseUrl/api/files/download?path=$encodedPath"
    }

    /**
     * Opens a WebSocket connection to the Debian backend using Engine.IO / Socket.IO v4 protocol
     * for realtime system_update and server_status events.
     */
    fun connectWebSocket(
        onEvent: (event: String, data: String) -> Unit,
        onStatusChange: (isConnected: Boolean) -> Unit
    ): WebSocket? {
        val host = ServerConfig.serverHost.value
        val port = ServerConfig.serverPort.value
        if (host.isBlank()) return null

        val scheme = if (ServerConfig.useHttps.value) "wss" else "ws"
        // The Debian server uses Flask-SocketIO which listens on /socket.io/?EIO=4&transport=websocket
        val wsUrl = "$scheme://$host:$port/socket.io/?EIO=4&transport=websocket"

        activeWebSocket?.close(1000, "Reconnecting")

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", AuthInterceptor.USER_AGENT)
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $wsUrl")
                onStatusChange(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    // Engine.IO Open packet (0{"sid":...})
                    text.startsWith("0") -> {
                        // Send Socket.IO CONNECT to root namespace
                        webSocket.send("40")
                    }
                    // Engine.IO Ping (2) -> Reply with Pong (3)
                    text == "2" -> {
                        webSocket.send("3")
                    }
                    // Socket.IO event: 42["event_name", {data}]
                    text.startsWith("42") -> {
                        try {
                            val jsonPayload = text.substring(2)
                            val jsonArray = org.json.JSONArray(jsonPayload)
                            if (jsonArray.length() >= 2) {
                                val event = jsonArray.getString(0)
                                val dataObj = jsonArray.get(1)
                                val dataStr = dataObj.toString()
                                onEvent(event, dataStr)
                            } else if (jsonArray.length() == 1) {
                                val event = jsonArray.getString(0)
                                onEvent(event, "{}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse Socket.IO event: ${e.message}")
                        }
                    }
                    // Socket.IO namespace connected (40)
                    text.startsWith("40") -> {
                        Log.d(TAG, "Socket.IO namespace connected")
                    }
                    // Fallback for standard JSON or raw messages
                    else -> {
                        try {
                            val json = org.json.JSONObject(text)
                            val event = json.optString("event", "message")
                            val data = json.optString("data", text)
                            onEvent(event, data)
                        } catch (_: Exception) {
                            onEvent("raw", text)
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onStatusChange(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failed on $wsUrl: ${t.message}")
                onStatusChange(false)
            }
        })

        return activeWebSocket
    }

    fun closeWebSocket() {
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }
}
