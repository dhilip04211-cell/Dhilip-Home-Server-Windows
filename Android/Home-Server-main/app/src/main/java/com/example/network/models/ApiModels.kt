package com.example.network.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "server") val server: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "uptime_seconds") val uptimeSeconds: Long? = null
)

@JsonClass(generateAdapter = true)
data class DiscoveryResponse(
    @Json(name = "service") val service: String? = null,
    @Json(name = "server") val server: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "port") val port: Int? = null,
    @Json(name = "lan_ip") val lanIp: String? = null,
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "api") val api: String? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "status") val status: String? = null
) {
    val resolvedName: String get() = service ?: server ?: name ?: deviceName ?: "DhilipHome Server"
    val resolvedIp: String get() = lanIp ?: ip ?: ""
}

@JsonClass(generateAdapter = true)
data class ServerInfoResponse(
    @Json(name = "name") val name: String? = null,
    @Json(name = "hostname") val hostname: String? = null,
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "port") val port: Int? = null,
    @Json(name = "os") val os: String? = null,
    @Json(name = "python_version") val pythonVersion: String? = null,
    @Json(name = "server_version") val serverVersion: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "uptime_seconds") val uptimeSeconds: Long? = null,
    @Json(name = "server_start_time") val serverStartTime: String? = null,
    @Json(name = "current_time") val currentTime: String? = null,
    @Json(name = "uptime") val uptime: String? = null,
    @Json(name = "server_time") val serverTime: String? = null
) {
    val resolvedVersion: String get() = version ?: serverVersion ?: "Unavailable"
}

@JsonClass(generateAdapter = true)
data class ThermalInfo(
    @Json(name = "cpu_temperature") val cpuTemperature: Double? = null,
    @Json(name = "temperature") val temperature: Double? = null,
    @Json(name = "temp_c") val tempC: Double? = null,
    @Json(name = "temperature_c") val temperatureC: Double? = null,
    @Json(name = "status") val status: String? = null
) {
    val resolvedTemperature: Double? get() = cpuTemperature ?: temperature ?: tempC ?: temperatureC
}

@JsonClass(generateAdapter = true)
data class CpuInfo(
    @Json(name = "usage_percent") val usagePercent: Double = 0.0,
    @Json(name = "physical_cores") val physicalCores: Int = 0,
    @Json(name = "logical_cores") val logicalCores: Int = 0,
    @Json(name = "frequency_mhz") val frequencyMhz: Double = 0.0,
    @Json(name = "temperature") val temperature: Double? = null,
    @Json(name = "temp_c") val tempC: Double? = null,
    @Json(name = "temperature_c") val temperatureC: Double? = null
) {
    val resolvedTemperature: Double? get() = temperature ?: tempC ?: temperatureC
}

@JsonClass(generateAdapter = true)
data class RamInfo(
    @Json(name = "total_bytes") val totalBytes: Long = 0L,
    @Json(name = "used_bytes") val usedBytes: Long = 0L,
    @Json(name = "available_bytes") val availableBytes: Long = 0L,
    @Json(name = "free_bytes") val freeBytes: Long = 0L,
    @Json(name = "usage_percent") val usagePercent: Double = 0.0,
    @Json(name = "total_human") val totalHuman: String? = null,
    @Json(name = "used_human") val usedHuman: String? = null,
    @Json(name = "free_human") val freeHuman: String? = null,
    @Json(name = "available_human") val availableHuman: String? = null
)

@JsonClass(generateAdapter = true)
data class SwapInfo(
    @Json(name = "total_bytes") val totalBytes: Long = 0L,
    @Json(name = "used_bytes") val usedBytes: Long = 0L,
    @Json(name = "free_bytes") val freeBytes: Long = 0L,
    @Json(name = "usage_percent") val usagePercent: Double = 0.0,
    @Json(name = "total_human") val totalHuman: String? = null,
    @Json(name = "used_human") val usedHuman: String? = null,
    @Json(name = "free_human") val freeHuman: String? = null
)

@JsonClass(generateAdapter = true)
data class UptimeInfo(
    @Json(name = "system_uptime_seconds") val systemUptimeSeconds: Long = 0L,
    @Json(name = "system_uptime_human") val systemUptimeHuman: String? = null,
    @Json(name = "server_uptime_seconds") val serverUptimeSeconds: Long = 0L,
    @Json(name = "server_uptime_human") val serverUptimeHuman: String? = null,
    @Json(name = "boot_time") val bootTime: String = "",
    @Json(name = "boot_timestamp") val bootTimestamp: Long = 0L,
    @Json(name = "uptime_seconds") val legacyUptimeSeconds: Long = 0L,
    @Json(name = "uptime_formatted") val legacyUptimeFormatted: String = ""
) {
    val uptimeSeconds: Long get() = if (systemUptimeSeconds > 0) systemUptimeSeconds else legacyUptimeSeconds
    val uptimeFormatted: String get() = systemUptimeHuman ?: serverUptimeHuman ?: legacyUptimeFormatted.ifBlank { "${uptimeSeconds}s" }
}

@JsonClass(generateAdapter = true)
data class OsInfo(
    @Json(name = "os_name") val osName: String? = null,
    @Json(name = "distribution") val distribution: String = "",
    @Json(name = "version") val version: String? = null,
    @Json(name = "kernel") val kernel: String = "",
    @Json(name = "architecture") val architecture: String = "",
    @Json(name = "hostname") val hostname: String = "",
    @Json(name = "python_version") val pythonVersion: String? = null,
    @Json(name = "operating_system") val legacyOperatingSystem: String = ""
) {
    val operatingSystem: String
        get() = when {
            legacyOperatingSystem.isNotBlank() -> legacyOperatingSystem
            distribution.isNotBlank() -> "$distribution ${version ?: ""}".trim()
            else -> osName ?: "Unavailable"
        }
}

@JsonClass(generateAdapter = true)
data class ProcessInfo(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "running_count") val runningCount: Int = 0,
    @Json(name = "running") val legacyRunning: Int = 0
) {
    val running: Int get() = if (runningCount > 0) runningCount else legacyRunning
}

@JsonClass(generateAdapter = true)
data class SystemInfoResponse(
    @Json(name = "cpu") val cpu: CpuInfo? = null,
    @Json(name = "memory") val memory: RamInfo? = null,
    @Json(name = "ram") val ram: RamInfo? = null,
    @Json(name = "swap") val swap: SwapInfo? = null,
    @Json(name = "uptime") val uptime: UptimeInfo? = null,
    @Json(name = "operating_system") val operatingSystem: OsInfo? = null,
    @Json(name = "os") val osLegacy: OsInfo? = null,
    @Json(name = "processes") val processes: ProcessInfo? = null,
    @Json(name = "temperature") val temperature: Double? = null,
    @Json(name = "temperature_c") val temperatureC: Double? = null,
    @Json(name = "thermal") val thermal: ThermalInfo? = null,
    @Json(name = "timestamp") val timestamp: String? = null
) {
    val resolvedRam: RamInfo? get() = ram ?: memory
    val os: OsInfo? get() = operatingSystem ?: osLegacy
    val resolvedTemperature: Double
        get() = temperature
            ?: temperatureC
            ?: cpu?.resolvedTemperature
            ?: thermal?.resolvedTemperature
            ?: 0.0
}

@JsonClass(generateAdapter = true)
data class StorageDriveResponse(
    @Json(name = "mount") val mount: String = "",
    @Json(name = "device") val device: String? = null,
    @Json(name = "filesystem") val filesystem: String = "",
    @Json(name = "total_bytes") val totalBytes: Long = 0L,
    @Json(name = "used_bytes") val usedBytes: Long = 0L,
    @Json(name = "free_bytes") val freeBytes: Long = 0L,
    @Json(name = "usage_percent") val usagePercent: Double = 0.0,
    @Json(name = "total_human") val totalHuman: String? = null,
    @Json(name = "used_human") val usedHuman: String? = null,
    @Json(name = "free_human") val freeHuman: String? = null
)

@JsonClass(generateAdapter = true)
data class StoragePartitionResponse(
    @Json(name = "mount") val mount: String = "",
    @Json(name = "filesystem") val filesystem: String = "",
    @Json(name = "total_bytes") val totalBytes: Long = 0L,
    @Json(name = "used_bytes") val usedBytes: Long = 0L,
    @Json(name = "free_bytes") val freeBytes: Long = 0L,
    @Json(name = "usage_percent") val usagePercent: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class NetworkInterfaceResponse(
    @Json(name = "name") val name: String = "",
    @Json(name = "status") val status: String = "",
    @Json(name = "ipv4") val ipv4: String? = null,
    @Json(name = "all_ipv4") val allIpv4: List<String>? = null,
    @Json(name = "ipv6") val ipv6: String? = null,
    @Json(name = "mac_address") val macAddress: String? = null,
    @Json(name = "speed_mbps") val speedMbps: Int? = null,
    @Json(name = "bytes_sent") val bytesSent: Long = 0L,
    @Json(name = "bytes_received") val bytesReceived: Long = 0L
)

@JsonClass(generateAdapter = true)
data class NetworkInfoResponse(
    @Json(name = "hostname") val hostname: String = "",
    @Json(name = "primary_ip") val primaryIp: String? = null,
    @Json(name = "lan_ip") val lanIpLegacy: String? = null,
    @Json(name = "port") val port: Int? = null,
    @Json(name = "lan_ips") val lanIps: List<String>? = null,
    @Json(name = "interfaces") val interfaces: List<NetworkInterfaceResponse> = emptyList()
) {
    val lanIp: String get() = primaryIp ?: lanIpLegacy ?: ""
}

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthUserData(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthTokenData(
    @Json(name = "token") val token: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "user") val user: AuthUserData? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "token") val rootToken: String? = null,
    @Json(name = "username") val rootUsername: String? = null,
    @Json(name = "data") val data: AuthTokenData? = null
) {
    val token: String? get() = data?.token ?: rootToken
    val username: String? get() = data?.user?.username ?: rootUsername
}

@JsonClass(generateAdapter = true)
data class AuthStatusResponse(
    @Json(name = "authenticated") val authenticated: Boolean = false,
    @Json(name = "username") val rootUsername: String? = null,
    @Json(name = "user") val user: AuthUserData? = null,
    @Json(name = "error") val error: String? = null
) {
    val username: String? get() = user?.username ?: rootUsername
}

@JsonClass(generateAdapter = true)
data class FileResponseItem(
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "is_folder") val isFolder: Boolean = false,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "modified") val modified: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateFolderRequest(
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String = ""
)


@JsonClass(generateAdapter = true)
data class RenameFileRequest(
    @Json(name = "path") val path: String,
    @Json(name = "new_name") val newName: String
)

@JsonClass(generateAdapter = true)
data class RemoteDownloadCancelRequest(
    @Json(name = "task_id") val taskId: String
)

@JsonClass(generateAdapter = true)
data class MediaResponseItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "category") val category: String = "Other",
    @Json(name = "size") val size: Long? = null,
    @Json(name = "duration") val duration: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "stream_url") val streamUrl: String? = null,
    @Json(name = "modified") val modified: String? = null
)
