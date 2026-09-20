package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.network.models.DiscoveryResponse
import com.example.network.models.HealthResponse
import com.example.network.models.ServerInfoResponse
import com.example.network.models.SystemInfoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

data class DiscoveredServer(
    val ip: String,
    val port: Int = ServerConfig.DEFAULT_PORT,
    val name: String = "DhilipHome Server",
    val version: String = "Unavailable"
)

object ServerConnectionManager {
    private const val TAG = "ServerConnectionManager"
    private const val HEALTH_CHECK_INTERVAL_MS = 15_000L
    private const val POLLING_FALLBACK_INTERVAL_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthCheckJob: Job? = null
    private var realtimeJob: Job? = null
    private var nsdManager: NsdManager? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _serverName = MutableStateFlow<String?>("DhilipHome Server")
    val serverName: StateFlow<String?> = _serverName.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _lastConnectionTime = MutableStateFlow(0L)
    val lastConnectionTime: StateFlow<Long> = _lastConnectionTime.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfoResponse?>(null)
    val serverInfo: StateFlow<ServerInfoResponse?> = _serverInfo.asStateFlow()

    private val _systemInfo = MutableStateFlow<SystemInfoResponse?>(null)
    val systemInfo: StateFlow<SystemInfoResponse?> = _systemInfo.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var consecutiveFailures = 0

    fun initialize(context: Context) {
        ServerConfig.initialize(context)
        ApiClient.authInterceptor.initialize(context)
        nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

        _serverIp.value = ServerConfig.serverHost.value

        startHealthMonitoring()
        startRealtimeMonitoring()
    }

    fun startHealthMonitoring() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                if (ServerConfig.isConfigured()) {
                    checkHealth()
                }
                // Back off if repeatedly failing to avoid hammering the network
                val delayMs = if (consecutiveFailures > 3) 30_000L else HEALTH_CHECK_INTERVAL_MS
                delay(delayMs)
            }
        }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        val apiService = ApiClient.getApiService()
        if (apiService == null || !ServerConfig.isConfigured()) {
            _isConnected.value = false
            _connectionError.value = "Server address not configured"
            ServerConfig.setConnectionState(ServerConnectionState.DISCONNECTED)
            return@withContext false
        }

        try {
            val response = apiService.getHealth()
            if (response.isSuccessful) {
                val health = response.body()
                _isConnected.value = true
                _serverVersion.value = health?.version
                _serverName.value = health?.server ?: "DhilipHome Server"
                _serverIp.value = ServerConfig.serverHost.value
                _lastConnectionTime.value = System.currentTimeMillis()
                _connectionError.value = null
                consecutiveFailures = 0
                ServerConfig.setConnectionState(ServerConnectionState.CONNECTED)

                // Refresh server info when online
                fetchServerInfo()
                true
            } else {
                handleConnectionFailure("Server returned HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            handleConnectionFailure("Connection failed: ${e.localizedMessage ?: "Server offline"}")
            false
        }
    }

    private fun handleConnectionFailure(error: String) {
        consecutiveFailures++
        _isConnected.value = false
        _connectionError.value = error
        ServerConfig.setConnectionState(ServerConnectionState.OFFLINE)
    }

    suspend fun fetchServerInfo(): ServerInfoResponse? = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext null
        try {
            val response = api.getServerInfo()
            if (response.isSuccessful) {
                val info = response.body()
                _serverInfo.value = info
                return@withContext info
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch /api/server: ${e.message}")
        }
        null
    }

    suspend fun fetchSystemInfo(): SystemInfoResponse? = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext null
        try {
            val response = api.getSystemInfo()
            if (response.isSuccessful) {
                val info = response.body()
                _systemInfo.value = info
                return@withContext info
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch /api/system: ${e.message}")
        }
        null
    }

    /**
     * Realtime monitor: Uses WebSocket with automatic fallback to periodic REST polling
     */
    fun startRealtimeMonitoring() {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            var isWsActive = false

            // Try connecting WebSocket
            if (ServerConfig.isConfigured()) {
                ApiClient.connectWebSocket(
                    onEvent = { event, data ->
                        if (event == "system_update") {
                            try {
                                parseSystemUpdateJson(data)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing ws system_update: ${e.message}")
                            }
                        } else if (event == "server_status") {
                            try {
                                val root = JSONObject(data)
                                val server = root.optString("server", "DhilipHome Server")
                                val version = root.optString("version", "unknown")
                                _isConnected.value = true
                                _serverName.value = server
                                _serverVersion.value = version
                                ServerConfig.setConnectionState(ServerConnectionState.CONNECTED)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing ws server_status: ${e.message}")
                            }
                        }
                    },
                    onStatusChange = { connected ->
                        isWsActive = connected
                        if (connected) {
                            _isConnected.value = true
                            ServerConfig.setConnectionState(ServerConnectionState.CONNECTED)
                        }
                    }
                )
            }

            // Polling loop (active if WS is not connected, or to periodically check)
            while (isActive) {
                if (_isConnected.value && !isWsActive) {
                    fetchSystemInfo()
                }
                delay(POLLING_FALLBACK_INTERVAL_MS)
            }
        }
    }

    private fun parseSystemUpdateJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val sysObj = if (root.has("system")) root.getJSONObject("system") else root
            // If parsed successfully, we can update systemInfo
            val cpuObj = sysObj.optJSONObject("cpu")
            val ramObj = sysObj.optJSONObject("memory") ?: sysObj.optJSONObject("ram")
            val cpuUsage = cpuObj?.optDouble("usage_percent", 0.0) ?: 0.0
            val ramUsage = ramObj?.optDouble("usage_percent", 0.0) ?: 0.0

            val current = _systemInfo.value
            if (current != null) {
                val updatedCpu = current.cpu?.copy(usagePercent = cpuUsage)
                    ?: com.example.network.models.CpuInfo(usagePercent = cpuUsage)
                val updatedRam = current.resolvedRam?.copy(usagePercent = ramUsage)
                    ?: com.example.network.models.RamInfo(usagePercent = ramUsage)
                _systemInfo.value = current.copy(cpu = updatedCpu, ram = updatedRam, memory = updatedRam)
            }
        } catch (_: Exception) {}
    }

    /**
     * Automated Server Discovery
     * Probes local network for DhilipHome Server via UDP broadcast, /api/discovery and /api/health
     */
    suspend fun discoverServer(timeoutMs: Long = 6000L): DiscoveredServer? = withContext(Dispatchers.IO) {
        _isDiscovering.value = true
        try {
            // 1. Try fast UDP broadcast discovery on Config.DISCOVERY_UDP_PORT (8888)
            val udpServer = discoverViaUdp()
            if (udpServer != null) {
                return@withContext udpServer
            }

            // 2. Check if configured host already responds to /api/discovery
            val configuredHost = ServerConfig.serverHost.value
            val configuredPort = ServerConfig.serverPort.value
            if (configuredHost.isNotBlank()) {
                val probe = probeServer(configuredHost, configuredPort)
                if (probe != null) return@withContext probe
            }

            // 3. Discover local IP subnet
            val localIps = getLocalSubnetPrefixes()
            val client = OkHttpClient.Builder()
                .connectTimeout(800, TimeUnit.MILLISECONDS)
                .readTimeout(800, TimeUnit.MILLISECONDS)
                .build()

            // Search common LAN hosts (e.g. .1, .2, .100, .101, .150, .182, etc.) and parallel probe
            for (subnet in localIps) {
                val candidates = mutableListOf<String>()
                // Prioritize standard server addresses
                listOf(100, 101, 102, 105, 150, 182, 200, 2, 3, 10, 20, 50, 80).forEach { lastOctet ->
                    candidates.add("$subnet.$lastOctet")
                }

                for (host in candidates) {
                    val server = probeServer(host, 8080, client)
                    if (server != null) {
                        return@withContext server
                    }
                }
            }

            null
        } finally {
            _isDiscovering.value = false
        }
    }

    private fun discoverViaUdp(): DiscoveredServer? {
        return try {
            val socket = java.net.DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500
            }
            val msg = "DHILIPHOME_DISCOVER".toByteArray()
            val broadcastAddr = java.net.InetAddress.getByName("255.255.255.255")
            val packet = java.net.DatagramPacket(msg, msg.size, broadcastAddr, 8888)
            socket.send(packet)

            val buffer = ByteArray(2048)
            val responsePacket = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            val responseStr = String(responsePacket.data, 0, responsePacket.length)
            socket.close()

            val json = JSONObject(responseStr)
            val ip = json.optString("lan_ip", responsePacket.address.hostAddress ?: "")
            val port = json.optInt("port", 8080)
            val name = json.optString("service", json.optString("server", "DhilipHome Server"))
            val version = json.optString("version", "unknown")
            if (ip.isNotBlank()) {
                DiscoveredServer(ip = ip, port = port, name = name, version = version)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun probeServer(host: String, port: Int, client: OkHttpClient = OkHttpClient()): DiscoveredServer? {
        val discoveryUrl = "http://$host:$port/api/discovery"
        val healthUrl = "http://$host:$port/api/health"

        try {
            // First try discovery endpoint
            val discReq = Request.Builder().url(discoveryUrl).get().build()
            val discRes = client.newCall(discReq).execute()
            if (discRes.isSuccessful) {
                val body = discRes.body?.string() ?: ""
                discRes.close()
                val json = JSONObject(body)
                val name = json.optString("service", json.optString("server", json.optString("name", "DhilipHome Server")))
                val version = json.optString("version", "unknown")
                val portFromResponse = json.optInt("port", port)
                val lanIp = json.optString("lan_ip", host)
                return DiscoveredServer(ip = if (lanIp.isNotBlank()) lanIp else host, port = portFromResponse, name = name, version = version)
            }
            discRes.close()
        } catch (_: Exception) {}

        try {
            // Fallback try health endpoint
            val healthReq = Request.Builder().url(healthUrl).get().build()
            val healthRes = client.newCall(healthReq).execute()
            if (healthRes.isSuccessful) {
                val body = healthRes.body?.string() ?: ""
                healthRes.close()
                var version = "unknown"
                var name = "DhilipHome Server"
                try {
                    val json = JSONObject(body)
                    version = json.optString("version", "unknown")
                    name = json.optString("server", json.optString("service", "DhilipHome Server"))
                } catch (_: Exception) {}
                return DiscoveredServer(ip = host, port = port, name = name, version = version)
            }
            healthRes.close()
        } catch (_: Exception) {}

        return null
    }

    private fun getLocalSubnetPrefixes(): List<String> {
        val prefixes = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        val parts = hostAddress.split(".")
                        if (parts.size == 4) {
                            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            if (!prefixes.contains(prefix)) {
                                prefixes.add(prefix)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (prefixes.isEmpty()) {
            prefixes.add("192.168.1")
            prefixes.add("192.168.0")
            prefixes.add("192.168.31")
        }
        return prefixes
    }
}
