package com.example.data.repository

import android.util.Log
import com.example.data.model.FileItem
import com.example.data.model.FileType
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.data.model.RecentActivity
import com.example.data.model.ServerStatus
import com.example.data.model.ServiceState
import com.example.data.model.ServiceStatus
import com.example.data.model.StorageInfo
import com.example.data.model.StoragePartition
import com.example.data.model.User
import com.example.domain.repository.ServerRepository
import com.example.network.ApiClient
import com.example.network.ServerConfig
import com.example.network.ServerConnectionManager
import com.example.network.models.CreateFolderRequest
import com.example.network.models.RenameFileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object HttpServerRepository : ServerRepository {
    private const val TAG = "HttpServerRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serverStatus = MutableStateFlow(
        ServerStatus(
            isOnline = false,
            statusText = "Connecting...",
            isDemoMode = false,
            cpuPercent = 0,
            ramUsedGb = 0.0,
            ramTotalGb = 0.0,
            storageUsedTb = 0.0,
            storageTotalTb = 0.0,
            uptimeDays = 0,
            networkStatus = "Not connected",
            temperatureCelsius = 0,
            loadAverage = "Unavailable",
            serverHostname = "",
            osVersion = "Unavailable",
            kernelVersion = "Unavailable"
        )
    )

    private val _storageInfo = MutableStateFlow(
        StorageInfo(
            totalTb = 0.0,
            usedTb = 0.0,
            freeTb = 0.0,
            usagePercent = 0,
            partitions = emptyList()
        )
    )

    private val _cachedFiles = MutableStateFlow<Map<String, List<FileItem>>>(emptyMap())
    private val _discoveredMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    private val _recentActivities = MutableStateFlow<List<RecentActivity>>(emptyList())

    init {
        // Observe connection manager and system info
        scope.launch {
            combine(
                ServerConnectionManager.isConnected,
                ServerConnectionManager.serverInfo,
                ServerConnectionManager.systemInfo,
                ServerConfig.baseUrl
            ) { isConnected, serverInfo, sysInfo, baseUrl ->
                val host = ServerConfig.serverHost.value
                val port = ServerConfig.serverPort.value
                val hostDisplay = if (host.isNotBlank()) "$host:$port" else "DhilipHome Server"


                val sysCpu = sysInfo?.cpu
                val sysRam = sysInfo?.resolvedRam
                val sysUptime = sysInfo?.uptime
                val sysOs = sysInfo?.os

                val cpuUsage = sysCpu?.usagePercent?.toInt() ?: 0

                val cpuCores = sysCpu?.physicalCores ?: 0
                val cpuLogicalCores = sysCpu?.logicalCores ?: 0
                val cpuFreq = sysCpu?.frequencyMhz ?: 0.0

                val ramTotalGb = (sysRam?.totalBytes ?: 0L) / 1_073_741_824.0
                val ramUsedGb = (sysRam?.usedBytes ?: 0L) / 1_073_741_824.0
                val ramFreeGb = (sysRam?.freeBytes ?: 0L) / 1_073_741_824.0

                val ramPercent = if (ramTotalGb > 0) ((ramUsedGb / ramTotalGb) * 100).toInt() else 0

                val swapInfo = sysInfo?.swap
                val swapTotalGb = (swapInfo?.totalBytes ?: 0L) / 1_073_741_824.0
                val swapUsedGb = (swapInfo?.usedBytes ?: 0L) / 1_073_741_824.0
                val swapPercent = if (swapTotalGb > 0) ((swapUsedGb / swapTotalGb) * 100).toInt() else 0

                val uptimeSec = sysUptime?.uptimeSeconds ?: 0L
                val uptimeDays = (uptimeSec / 86400L).toInt()
                val uptimeFormatted = sysUptime?.uptimeFormatted ?: "Unavailable"

                val osDesc = serverInfo?.os ?: sysOs?.operatingSystem ?: "Unavailable"
                val kernelDesc = sysOs?.kernel ?: "Unavailable"

                val temp = sysInfo?.resolvedTemperature?.toInt() ?: 0

                val thermalStatusStr = when {
                    temp >= 80 -> "High Thermal ($temp°C)"
                    temp >= 65 -> "Warm ($temp°C)"
                    temp > 0 -> "Normal ($temp°C)"
                    else -> "Sensor N/A"
                }

                val currentStorage = _storageInfo.value
                val storageUsed = currentStorage.usedTb
                val storageTotal = currentStorage.totalTb
                val storageFree = currentStorage.freeTb
                val storagePct = currentStorage.usagePercent

                val procTotal = sysInfo?.processes?.totalCount ?: 0
                val procRunning = sysInfo?.processes?.running ?: 0

                ServerStatus(
                    isOnline = isConnected,
                    statusText = if (isConnected) "Online (${ServerConnectionManager.serverVersion.value ?: "Unavailable"})" else "Offline",
                    isDemoMode = false,
                    cpuPercent = cpuUsage,
                    cpuCores = cpuCores,
                    cpuLogicalCores = cpuLogicalCores,
                    cpuFrequencyMhz = cpuFreq,
                    ramUsedGb = ((ramUsedGb * 10).toInt()) / 10.0,
                    ramTotalGb = ((ramTotalGb * 10).toInt()) / 10.0,
                    ramFreeGb = ((ramFreeGb * 10).toInt()) / 10.0,
                    ramAvailableGb = ((ramFreeGb * 10).toInt()) / 10.0,
                    ramUsagePercent = ramPercent,
                    swapUsedGb = ((swapUsedGb * 10).toInt()) / 10.0,
                    swapTotalGb = ((swapTotalGb * 10).toInt()) / 10.0,
                    swapPercent = swapPercent,
                    storageUsedTb = ((storageUsed * 100).toInt()) / 100.0,
                    storageTotalTb = ((storageTotal * 100).toInt()) / 100.0,
                    storageFreeTb = ((storageFree * 100).toInt()) / 100.0,
                    storageUsagePercent = storagePct,
                    uptimeDays = uptimeDays,
                    uptimeFormatted = uptimeFormatted,
                    networkStatus = if (isConnected) "Connected to $hostDisplay" else "Not connected",
                    serverIp = if (isConnected) host else "",
                    serverPort = port,
                    temperatureCelsius = temp,
                    thermalStatus = thermalStatusStr,
                    totalProcesses = procTotal,
                    runningProcesses = procRunning,
                    loadAverage = "Unavailable",
                    serverHostname = if (isConnected) hostDisplay else "",
                    osVersion = osDesc,
                    kernelVersion = kernelDesc
                )
            }.collect { status ->
                _serverStatus.value = status
            }
        }

        // Fetch initial storage info and media catalog
        scope.launch {
            refreshStorageInfo()
            refreshMediaCatalog()
            refreshDashboardLists()
        }
    }

    suspend fun refreshServerStatus(): ServerStatus = withContext(Dispatchers.IO) {
        val host = ServerConfig.serverHost.value
        val port = ServerConfig.serverPort.value
        val result = ServerConfig.testConnection(targetHost = host, targetPort = port)

        if (result.isSuccess) {
            ServerConnectionManager.checkHealth()
            ServerConnectionManager.fetchSystemInfo()
            ServerConnectionManager.fetchServerInfo()
            refreshStorageInfo()
            refreshMediaCatalog()
            refreshDashboardLists()
        }

        val current = _serverStatus.value
        val hostDisplay = if (host.isNotBlank()) "$host:$port" else "Unconfigured"

        val updated = current.copy(
            isOnline = result.isSuccess,
            statusText = if (result.isSuccess) "Online (${result.latencyMs}ms)" else "Offline",
            serverHostname = hostDisplay,
            networkStatus = if (result.isSuccess) "Connected to $hostDisplay" else "Cannot reach $hostDisplay"
        )
        _serverStatus.value = updated
        updated
    }

    suspend fun refreshStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService()
        if (api != null && ServerConfig.isConfigured()) {
            try {
                val response = api.getStorage()
                if (response.isSuccessful) {
                    val bodyStr = response.body()?.string() ?: ""
                    val partitions = parseStorageJson(bodyStr)
                    if (partitions.isNotEmpty()) {
                        var totalBytes = 0L
                        var usedBytes = 0L
                        partitions.forEach {
                            totalBytes += (it.totalGb * 1_073_741_824.0).toLong()
                            usedBytes += (it.usedGb * 1_073_741_824.0).toLong()
                        }
                        val totalTb = totalBytes / 1_099_511_627_776.0
                        val usedTb = usedBytes / 1_099_511_627_776.0
                        val freeTb = (totalBytes - usedBytes).coerceAtLeast(0) / 1_099_511_627_776.0
                        val usagePct = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

                        val newStorage = StorageInfo(
                            totalTb = ((totalTb * 100).toInt()) / 100.0,
                            usedTb = ((usedTb * 100).toInt()) / 100.0,
                            freeTb = ((freeTb * 100).toInt()) / 100.0,
                            usagePercent = usagePct,
                            partitions = partitions
                        )
                        _storageInfo.value = newStorage
                        return@withContext newStorage
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching /api/storage: ${e.message}")
            }
        }

        return@withContext _storageInfo.value
    }

    internal fun parseStorageJson(jsonStr: String): List<StoragePartition> {
        val list = mutableListOf<StoragePartition>()
        try {
            val trimmed = jsonStr.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    when {
                        root.has("drives") -> root.getJSONArray("drives")
                        root.has("partitions") -> root.getJSONArray("partitions")
                        root.has("storage") -> root.getJSONArray("storage")
                        root.has("filesystems") -> root.getJSONArray("filesystems")
                        root.has("disks") -> root.getJSONArray("disks")
                        else -> null
                    }
                }
                else -> null
            }

            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val mount = obj.optString("mount", obj.optString("mount_point", "/"))
                    val fs = obj.optString("filesystem", obj.optString("fs", "ext4"))
                    val totalBytes = obj.optLong("total_bytes", obj.optLong("total", 0L))
                    val usedBytes = obj.optLong("used_bytes", obj.optLong("used", 0L))
                    val usagePercent = obj.optDouble("usage_percent", obj.optDouble("percent", 0.0)).toInt()

                    val totalGb = totalBytes / 1_073_741_824.0
                    val usedGb = usedBytes / 1_073_741_824.0

                    list.add(
                        StoragePartition(
                            mountPoint = mount,
                            label = if (mount == "/") "Root Filesystem" else mount.trimStart('/'),
                            filesystem = fs,
                            totalGb = ((totalGb * 10).toInt()) / 10.0,
                            usedGb = ((usedGb * 10).toInt()) / 10.0,
                            usagePercent = usagePercent
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    override fun getServerStatus(): Flow<ServerStatus> = _serverStatus.asStateFlow()

    override fun getFiles(directoryPath: String): Flow<List<FileItem>> = flow {
        val safePath = sanitizePath(directoryPath)
        val serverBase = ServerConfig.baseUrl.value.trimEnd('/')

        // The Debian server accepts path relative to DATA_ROOT (empty string or subfolder)
        val queryPath = if (safePath == "/" || safePath.isBlank()) "" else safePath.trimStart('/')
        val api = ApiClient.getApiService()
        var loadedFiles: List<FileItem>? = null

        if (api != null && serverBase.isNotBlank()) {
            try {
                val response = api.listFiles(queryPath)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string() ?: ""
                    loadedFiles = parseJsonFileList(bodyString, safePath, serverBase)
                }
            } catch (e: Exception) {
                Log.d(TAG, "listFiles failed for path '$queryPath': ${e.message}")
            }

            if (loadedFiles.isNullOrEmpty()) {
                try {
                    val response = api.getFiles(queryPath)
                    if (response.isSuccessful) {
                        val bodyString = response.body()?.string() ?: ""
                        loadedFiles = parseJsonFileList(bodyString, safePath, serverBase)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "getFiles failed for path '$queryPath': ${e.message}")
                }
            }
        }

        val result = loadedFiles ?: emptyList()
        _cachedFiles.value = _cachedFiles.value + (safePath to result)
        updateDiscoveredMedia(result)
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * Prevents directory traversal attacks (../)
     */
    private fun sanitizePath(path: String): String {
        var clean = path.replace("\\", "/")
        while (clean.contains("../")) {
            clean = clean.replace("../", "")
        }
        clean = clean.replace("..", "")
        if (!clean.startsWith("/")) clean = "/$clean"
        return clean
    }

    internal fun parseHtmlFileList(html: String, directoryPath: String, serverBase: String = ""): List<FileItem> {
        val files = mutableListOf<FileItem>()
        val linkPattern = Pattern.compile(
            "<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"[^>]*>(.*?)<\\/a>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = linkPattern.matcher(html)

        val cleanDir = if (directoryPath.endsWith("/")) directoryPath else "$directoryPath/"
        var index = 0

        while (matcher.find()) {
            val rawHref = matcher.group(1)?.trim() ?: continue
            val linkText = matcher.group(2)?.trim()?.replace(Regex("<[^>]*>"), "") ?: ""

            if (rawHref == "../" || rawHref == ".." || rawHref == "/" || rawHref.startsWith("?") || rawHref.startsWith("#") || rawHref.contains("sort=")) {
                continue
            }
            if (linkText == "Parent Directory" || linkText == ".." || linkText == "../") {
                continue
            }

            val decodedHref = try {
                URLDecoder.decode(rawHref, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                rawHref
            }

            val isFolder = rawHref.endsWith("/") || linkText.endsWith("/")
            val cleanName = decodedHref.trimEnd('/').substringAfterLast('/')

            if (cleanName.isBlank()) continue

            val fileType = if (isFolder) FileType.FOLDER else detectFileType(cleanName)
            val itemPath = "${cleanDir}${cleanName}${if (isFolder) "/" else ""}"
            val downloadUrl = ApiClient.getDownloadUrl(itemPath)

            val item = FileItem(
                id = "file_${directoryPath}_${index++}_$cleanName",
                name = cleanName,
                path = itemPath,
                isFolder = isFolder,
                type = fileType,
                sizeBytes = null,
                formattedSize = if (isFolder) null else "Server File",
                modifiedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                downloadUrl = downloadUrl
            )
            files.add(item)
        }

        return files.sortedWith(compareBy<FileItem> { !it.isFolder }.thenBy { it.name.lowercase() })
    }

    internal fun parseJsonFileList(jsonStr: String, directoryPath: String, serverBase: String = ""): List<FileItem> {
        val files = mutableListOf<FileItem>()
        try {
            val cleanDir = if (directoryPath.endsWith("/")) directoryPath else "$directoryPath/"
            val jsonTrim = jsonStr.trim()
            val root = if (jsonTrim.startsWith("{")) JSONObject(jsonTrim) else null
            val rootArray = if (jsonTrim.startsWith("[")) JSONArray(jsonTrim) else null

            val dataObj = root?.optJSONObject("data")
            val dirsArray = dataObj?.optJSONArray("directories") ?: root?.optJSONArray("directories")
            val filesArray = dataObj?.optJSONArray("files")
                ?: root?.optJSONArray("files")
                ?: dataObj?.optJSONArray("items")
                ?: root?.optJSONArray("items")
                ?: (if (dataObj == null && root?.optJSONArray("data") != null) root.getJSONArray("data") else null)
                ?: rootArray

            var index = 0

            // 1. Process directories
            if (dirsArray != null) {
                for (i in 0 until dirsArray.length()) {
                    val obj = dirsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "folder_$i")
                    val rawPath = obj.optString("path", name).trimStart('/')
                    val itemPath = if (rawPath.isNotBlank()) "/$rawPath/" else "${cleanDir}${name}/"
                    val modified = obj.optString("modified_time", obj.optString("modified", "Folder"))

                    files.add(
                        FileItem(
                            id = "dir_${directoryPath}_${index++}_$name",
                            name = name,
                            path = itemPath,
                            isFolder = true,
                            type = FileType.FOLDER,
                            sizeBytes = null,
                            formattedSize = null,
                            modifiedDate = modified.ifBlank { "Folder" },
                            downloadUrl = null
                        )
                    )
                }
            }

            // 2. Process files
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name", obj.optString("filename", "file_$i"))
                    val isDir = obj.optBoolean("is_dir", obj.optBoolean("is_folder", obj.optBoolean("isDir", false)))
                    val size = if (obj.has("size_bytes")) obj.optLong("size_bytes") else if (obj.has("size")) obj.optLong("size") else null
                    val sizeHuman = obj.optString("size_human", if (size != null && size > 0) formatBytes(size) else "")
                    val modified = obj.optString("modified_time", obj.optString("modified", obj.optString("updated", "")))
                    val rawPath = obj.optString("path", name).trimStart('/')

                    val type = if (isDir) FileType.FOLDER else detectFileType(name)
                    val itemPath = if (rawPath.isNotBlank()) "/$rawPath" else "${cleanDir}${name}"
                    val downloadUrl = ApiClient.getDownloadUrl(itemPath)

                    files.add(
                        FileItem(
                            id = "file_${directoryPath}_${index++}_$name",
                            name = name,
                            path = itemPath,
                            isFolder = isDir,
                            type = type,
                            sizeBytes = size,
                            formattedSize = if (isDir) null else sizeHuman.ifBlank { formatBytes(size ?: 0L) },
                            modifiedDate = modified.ifBlank { "Recent" },
                            downloadUrl = downloadUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing file list: ${e.message}")
        }
        return files.sortedWith(compareBy<FileItem> { !it.isFolder }.thenBy { it.name.lowercase() })
    }

    override suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext false
        try {
            val safeParent = sanitizePath(parentPath)
            api.createFolder(CreateFolderRequest(name = name.trim(), path = safeParent.trimStart('/'))).isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create folder: ${e.message}")
            false
        }
    }

    override suspend fun uploadFile(file: File, destinationFolder: String): Boolean = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext false
        try {
            val safeFolder = sanitizePath(destinationFolder)
            val reqFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, reqFile)
            val pathPart = safeFolder.toRequestBody("text/plain".toMediaTypeOrNull())

            val res = api.uploadFile(body, pathPart)
            res.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: ${e.message}")
            false
        }
    }

    private fun detectFileType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "webm", "flv", "m4v", "ts" -> FileType.VIDEO
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus" -> FileType.AUDIO
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> FileType.IMAGE
            "pdf" -> FileType.PDF
            "doc", "docx", "txt", "md", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "log" -> FileType.DOCUMENT
            "zip", "tar", "gz", "7z", "rar", "bz2", "xz", "iso", "apk" -> FileType.ARCHIVE
            else -> FileType.OTHER
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun updateDiscoveredMedia(files: List<FileItem>) {
        val mediaFiles = files.filter { it.type == FileType.VIDEO || it.type == FileType.AUDIO || it.type == FileType.IMAGE }
        if (mediaFiles.isEmpty()) return

        val newMedia = mediaFiles.map { file ->
            val category = when (file.type) {
                FileType.VIDEO -> MediaCategory.VIDEOS
                FileType.AUDIO -> MediaCategory.MUSIC
                FileType.IMAGE -> MediaCategory.PHOTOS
                else -> MediaCategory.ALL
            }
            val streamUrl = ApiClient.getStreamUrl(file.path)
            MediaItem(
                id = file.id,
                title = file.name.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                category = category,
                year = parseMediaYear(file.modifiedDate),
                duration = "",
                genre = "Server Media",
                rating = 0.0,
                description = "Streaming from ${ServerConfig.serverHost.value}:${ServerConfig.serverPort.value}",
                posterGradientColor = when (category) {
                    MediaCategory.MOVIES -> 0xFF1E3A8A
                    MediaCategory.MUSIC -> 0xFF6B21A8
                    MediaCategory.PHOTOS -> 0xFF065F46
                    else -> 0xFF1E293B
                },
                backdropGradientColor = 0xFF0F172A,
                progress = 0.0f,
                isFavorite = _favorites.value.contains(file.id),
                isContinueWatching = false,
                isRecentlyAdded = isRecentlyAdded(file.modifiedDate),
                isRecommended = false,
                resolution = if (file.type == FileType.VIDEO) "Stream" else "",
                audioFormat = if (file.type == FileType.AUDIO) "Audio" else "",
                fileSizeBytes = file.formattedSize ?: "",
                streamUrl = streamUrl,
                filePath = file.path
            )
        }

        val current = _discoveredMedia.value.associateBy { it.id }.toMutableMap()
        for (item in newMedia) {
            current[item.id] = item
        }
        _discoveredMedia.value = current.values.toList()
    }

    suspend fun refreshMediaCatalog(category: MediaCategory = MediaCategory.ALL): List<MediaItem> = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext emptyList()
        try {
            // Keep the server catalog synchronized with the real MEDIA_ROOT before reading it.
            if (category == MediaCategory.ALL) {
                try { api.triggerMediaScan() } catch (_: Exception) {}
            }
            val catParam = when (category) {
                MediaCategory.VIDEOS -> "Videos"
                MediaCategory.MOVIES -> "Movies"
                MediaCategory.TV_SHOWS -> "TV"
                MediaCategory.MUSIC -> "Music"
                MediaCategory.PHOTOS -> "Images"
                MediaCategory.ALL -> null
            }
            val res = api.getMedia(catParam)
            if (res.isSuccessful) {
                val bodyStr = res.body()?.string() ?: ""
                val items = parseMediaCatalogJson(bodyStr)
                if (items.isNotEmpty()) {
                    val map = _discoveredMedia.value.associateBy { it.id }.toMutableMap()
                    for (it in items) {
                        map[it.id] = it
                    }
                    _discoveredMedia.value = map.values.toList()
                    return@withContext items
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh media catalog: ${e.message}")
        }
        _discoveredMedia.value
    }

    private fun parseMediaYear(value: String): Int {
        return try {
            value.take(4).toInt().takeIf { it in 1900..2100 } ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun isRecentlyAdded(value: String): Boolean {
        return try {
            val instant = java.time.Instant.parse(value)
            java.time.Duration.between(instant, java.time.Instant.now()).toHours() <= 7L * 24L
        } catch (_: Exception) { false }
    }

    private fun parseMediaCatalogJson(jsonStr: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(jsonStr)
            val dataObj = root.optJSONObject("data")
            val itemsArr = dataObj?.optJSONArray("items") ?: root.optJSONArray("items") ?: root.optJSONArray("data")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "media_$i")
                    val filename = obj.optString("filename", obj.optString("name", "Media $i"))
                    val relPath = obj.optString("path", obj.optString("relative_path", filename))
                    val catStr = obj.optString("category", "Other")
                    val cat = when (catStr.lowercase()) {
                        "videos", "video" -> MediaCategory.VIDEOS
                        "movies", "movie" -> MediaCategory.MOVIES
                        "tv", "tv_shows", "series" -> MediaCategory.TV_SHOWS
                        "music", "audio" -> MediaCategory.MUSIC
                        "images", "photos", "photo" -> MediaCategory.PHOTOS
                        else -> MediaCategory.ALL
                    }
                    val sizeBytes = obj.optLong("size_bytes", 0L)
                    val sizeHuman = obj.optString("size_human", formatBytes(sizeBytes))
                    val streamUrl = ApiClient.getStreamUrl(relPath)

                    list.add(
                        MediaItem(
                            id = id,
                            title = filename.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                            category = cat,
                            year = parseMediaYear(obj.optString("modified_time")),
                            duration = "Unknown",
                            genre = catStr,
                            rating = 0.0,
                            description = "Server media at $relPath",
                            posterGradientColor = when (cat) {
                                MediaCategory.VIDEOS -> 0xFF1E3A8A
                                MediaCategory.MOVIES -> 0xFF1E3A8A
                                MediaCategory.MUSIC -> 0xFF6B21A8
                                MediaCategory.PHOTOS -> 0xFF065F46
                                else -> 0xFF1E293B
                            },
                            backdropGradientColor = 0xFF0F172A,
                            progress = 0.0f,
                            isFavorite = _favorites.value.contains(id),
                            isContinueWatching = false,
                            isRecentlyAdded = isRecentlyAdded(obj.optString("modified_time")),
                            isRecommended = false,
                            resolution = if (cat == MediaCategory.VIDEOS || cat == MediaCategory.MOVIES || cat == MediaCategory.TV_SHOWS) "Stream" else "",
                            audioFormat = if (cat == MediaCategory.MUSIC) "Audio" else "",
                            fileSizeBytes = sizeHuman,
                            streamUrl = streamUrl,
                            filePath = relPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing media catalog: ${e.message}")
        }
        return list
    }

    private val _services = MutableStateFlow<List<ServiceStatus>>(emptyList())
    private val _users = MutableStateFlow<List<User>>(emptyList())

    override fun getMedia(category: MediaCategory): Flow<List<MediaItem>> = _discoveredMedia.map { serverList ->
        if (category == MediaCategory.ALL) serverList
        else serverList.filter { it.category == category }
    }

    override fun getMediaById(id: String): Flow<MediaItem?> = _discoveredMedia.map { list ->
        list.find { it.id == id }
    }

    override fun getContinueWatching(): Flow<List<MediaItem>> = _discoveredMedia.map { list ->
        list.filter { it.isContinueWatching || it.progress > 0 }
    }

    override fun getRecentlyAddedMedia(): Flow<List<MediaItem>> = _discoveredMedia.map { list ->
        list.filter { it.isRecentlyAdded }
    }

    override fun getFavoriteMedia(): Flow<List<MediaItem>> = _discoveredMedia.map { list ->
        list.filter { _favorites.value.contains(it.id) }
    }

    suspend fun restartService(serviceName: String) = withContext(Dispatchers.IO) {
        _services.update { list ->
            list.map { svc ->
                if (serviceName.equals("all", ignoreCase = true) || svc.name.contains(serviceName, ignoreCase = true) || svc.serviceUnit.contains(serviceName, ignoreCase = true)) {
                    svc.copy(state = ServiceState.RESTARTING)
                } else svc
            }
        }
        kotlinx.coroutines.delay(1000)
        _services.update { list ->
            list.map { svc ->
                if (serviceName.equals("all", ignoreCase = true) || svc.name.contains(serviceName, ignoreCase = true) || svc.serviceUnit.contains(serviceName, ignoreCase = true)) {
                    svc.copy(state = ServiceState.RUNNING, uptime = "Just restarted")
                } else svc
            }
        }
    }

    suspend fun toggleService(serviceUnit: String) = withContext(Dispatchers.IO) {
        _services.update { list ->
            list.map { svc ->
                if (svc.serviceUnit == serviceUnit) {
                    val newState = if (svc.state == ServiceState.RUNNING) ServiceState.STOPPED else ServiceState.RUNNING
                    svc.copy(
                        state = newState,
                        uptime = if (newState == ServiceState.RUNNING) "Active (running)" else "Inactive (dead)"
                    )
                } else svc
            }
        }
    }

    fun addUser(username: String, displayName: String, role: com.example.data.model.UserRole) {
        val newUser = User(
            id = "usr_${System.currentTimeMillis()}",
            username = username.trim().lowercase(),
            displayName = displayName.ifBlank { username },
            role = role,
            lastActive = "Never",
            status = "Active"
        )
        _users.update { listOf(newUser) + it }
    }

    fun deleteUser(userId: String) {
        _users.update { list -> list.filter { it.id != userId } }
    }

    suspend fun rebootServer() = withContext(Dispatchers.IO) {
        _serverStatus.update { it.copy(isOnline = false, statusText = "Rebooting...") }
        kotlinx.coroutines.delay(2000)
        refreshServerStatus()
    }

    suspend fun shutdownServer() = withContext(Dispatchers.IO) {
        _serverStatus.update { it.copy(isOnline = false, statusText = "Shutdown (Standby)") }
    }

    override fun getStorageInfo(): Flow<StorageInfo> = _storageInfo.asStateFlow()

    override fun getRecentActivities(): Flow<List<RecentActivity>> = _recentActivities.asStateFlow()

    private suspend fun refreshDashboardLists() = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext
        try {
            api.getServices().takeIf { it.isSuccessful }?.body()?.string()?.let { _services.value = parseServicesJson(it) }
            api.getUsers().takeIf { it.isSuccessful }?.body()?.string()?.let { _users.value = parseUsersJson(it) }
            api.getActivity().takeIf { it.isSuccessful }?.body()?.string()?.let { _recentActivities.value = parseActivityJson(it) }
        } catch (e: Exception) { Log.w(TAG, "Dashboard lists refresh failed: ${e.message}") }
    }

    private fun parseServicesJson(json: String): List<ServiceStatus> {
        val arr = JSONObject(json).optJSONObject("data")?.optJSONArray("items") ?: JSONObject(json).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val state = when (o.optString("state").lowercase()) {
                    "running", "active" -> ServiceState.RUNNING
                    "stopped", "inactive", "failed" -> ServiceState.STOPPED
                    else -> ServiceState.UNKNOWN
                }
                add(ServiceStatus(o.optString("name", o.optString("unit")), o.optString("unit"), state,
                    if (o.has("port") && !o.isNull("port")) o.optInt("port") else null,
                    o.optString("description", ""), o.optString("uptime", "Unavailable"), o.optInt("memory_usage_mb", 0)))
            }
        }
    }

    private fun parseUsersJson(json: String): List<User> {
        val arr = JSONObject(json).optJSONObject("data")?.optJSONArray("items") ?: JSONObject(json).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val role = if (o.optString("role").equals("admin", true)) com.example.data.model.UserRole.ADMIN else com.example.data.model.UserRole.USER
                add(User(o.optString("id"), o.optString("username"), o.optString("display_name", o.optString("username")), role,
                    o.optString("last_active", "Never"), o.optString("status", "Active")))
            }
        }
    }

    private fun parseActivityJson(json: String): List<RecentActivity> {
        val arr = JSONObject(json).optJSONObject("data")?.optJSONArray("items") ?: JSONObject(json).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = when (o.optString("type").lowercase()) {
                    "video" -> FileType.VIDEO; "audio" -> FileType.AUDIO; "image" -> FileType.IMAGE; "folder" -> FileType.FOLDER
                    "pdf" -> FileType.PDF; "document" -> FileType.DOCUMENT; "archive" -> FileType.ARCHIVE; else -> FileType.OTHER
                }
                add(RecentActivity(o.optString("id"), o.optString("title"), o.optString("type_name", type.name), o.optString("timestamp", ""), type, o.optString("size_text").ifBlank { null }))
            }
        }
    }

    override fun getServices(): Flow<List<ServiceStatus>> = _services.asStateFlow()
    override fun getUsers(): Flow<List<User>> = _users.asStateFlow()

    override suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext false
        try {
            api.deleteFile(sanitizePath(path)).isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}")
            false
        }
    }

    override suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val api = ApiClient.getApiService() ?: return@withContext false
        try {
            api.renameFile(RenameFileRequest(sanitizePath(path), newName.trim())).isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Rename failed: ${e.message}")
            false
        }
    }

    override suspend fun toggleMediaFavorite(id: String): Boolean {
        val currentFavs = _favorites.value.toMutableSet()
        val isNowFav = if (currentFavs.contains(id)) {
            currentFavs.remove(id)
            false
        } else {
            currentFavs.add(id)
            true
        }
        _favorites.value = currentFavs

        _discoveredMedia.value = _discoveredMedia.value.map { item ->
            if (item.id == id) {
                item.copy(isFavorite = isNowFav)
            } else {
                item
            }
        }
        return isNowFav
    }

    fun recordActivity(title: String, subtitle: String, type: FileType) {
        val newActivity = RecentActivity(
            id = "act_${System.currentTimeMillis()}",
            title = title,
            typeName = type.name.lowercase().replaceFirstChar { it.uppercase() },
            timestamp = "Just now",
            fileType = type,
            sizeText = subtitle
        )
        val list = _recentActivities.value.toMutableList()
        list.add(0, newActivity)
        if (list.size > 20) {
            _recentActivities.value = list.subList(0, 20)
        } else {
            _recentActivities.value = list
        }
    }

    suspend fun notifyFilesChanged(path: String = "/") {
        try {
            refreshStorageInfo()
            refreshServerStatus()
            refreshMediaCatalog()
        } catch (e: Exception) {
            Log.w(TAG, "Error refreshing after file change: ${e.message}")
        }
    }
}
