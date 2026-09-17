package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.network.ApiClient
import com.example.network.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Thin client for server-authoritative downloads.
 *
 * The Android device NEVER downloads the cloud file. It submits a job to the
 * Home Server and observes the persistent server job until completion.
 */
object CloudDownloadManager {
    private const val TAG = "CloudDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()
    private val serverTaskIds = mutableMapOf<String, String>()
    private val _tasks = MutableStateFlow<List<CloudDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CloudDownloadTask>> = _tasks.asStateFlow()

    fun initialize(context: Context) {
        // Rehydrate from the server. Local Android cache is deliberately ignored.
        scope.launch { syncFromServer() }
    }

    fun deriveFilename(urlStr: String): String = try {
        val raw = URL(urlStr).path.substringAfterLast('/').trim()
        if (raw.isNotBlank()) URLDecoder.decode(raw, "UTF-8")
        else "download_${System.currentTimeMillis()}.bin"
    } catch (_: Exception) { "download_${System.currentTimeMillis()}.bin" }

    private fun authBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        ApiClient.authInterceptor.getToken()?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder
    }

    private fun executeFast(request: Request): okhttp3.Response {
        // A command/status request must fail fast. The actual file transfer happens
        // in the Debian server worker, so this timeout never limits the cloud file.
        return ApiClient.okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
    }

    fun startDownload(
        url: String,
        destinationFolder: String = "/",
        customFilename: String? = null,
        onComplete: (() -> Unit)? = null
    ): String {
        val taskId = "dl_${UUID.randomUUID().toString().take(10)}"
        val filename = customFilename?.trim().takeUnless { it.isNullOrBlank() } ?: deriveFilename(url)
        _tasks.update {
            listOf(
                CloudDownloadTask(
                    taskId, url.trim(), filename, destinationFolder,
                    status = CloudDownloadStatus.STORING_TO_SERVER,
                    speedText = "Sending to server…"
                )
            ) + it
        }
        activeJobs[taskId] = scope.launch {
            execute(taskId, url.trim(), filename, destinationFolder, onComplete)
        }
        return taskId
    }

    private suspend fun execute(
        taskId: String,
        url: String,
        filename: String,
        destination: String,
        onComplete: (() -> Unit)?
    ) {
        if (!ServerConfig.isConfigured()) {
            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server is not configured") }
            return
        }
        try {
            updateTask(taskId) { it.copy(status = CloudDownloadStatus.STORING_TO_SERVER, speedText = "Sending to server…") }

            val payload = JSONObject().apply {
                put("url", url)
                put("filename", filename)
                put("destination", destination.trim().ifBlank { "/" })
            }.toString()

            val startUrl = ServerConfig.baseUrl.value.trimEnd('/') + "/api/files/remote-download"
            val request = authBuilder(startUrl)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                executeFast(request)
            } catch (e: Exception) {
                throw IllegalStateException("Server did not respond to download request: ${e.message ?: "connection timeout"}")
            }

            val body = response.use { it.body?.string().orEmpty() }
            if (!response.isSuccessful) {
                throw IllegalStateException(parseError(body, "Server rejected download (HTTP ${response.code})"))
            }

            val data = JSONObject(body).optJSONObject("data") ?: JSONObject(body)
            val serverId = data.optString("task_id")
            if (serverId.isBlank()) throw IllegalStateException("Server did not return a download task ID")

            serverTaskIds[taskId] = serverId
            pollServerTask(taskId, serverId, onComplete)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud download command failed", e)
            updateTask(taskId) {
                it.copy(status = CloudDownloadStatus.FAILED, errorMessage = e.message ?: "Server download failed")
            }
        } finally {
            activeJobs.remove(taskId)
            serverTaskIds.remove(taskId)
        }
    }

    private suspend fun pollServerTask(taskId: String, serverId: String, onComplete: (() -> Unit)?) {
        var transientFailures = 0
        while (true) {
            delay(800)
            try {
                val statusUrl = ServerConfig.baseUrl.value.trimEnd('/') +
                    "/api/files/remote-download?task_id=" +
                    URLEncoder.encode(serverId, "UTF-8")
                val poll = executeFast(authBuilder(statusUrl).get().build())
                val body = poll.use { it.body?.string().orEmpty() }
                if (!poll.isSuccessful) throw IllegalStateException("HTTP ${poll.code}: ${parseError(body, "status request failed")}")

                val obj = JSONObject(body).optJSONObject("data") ?: JSONObject(body)
                val status = obj.optString("status", "queued").lowercase()
                val downloaded = obj.optLong("downloaded_bytes", 0L)
                val total = obj.optLong("total_bytes", 0L)
                val percent = obj.optInt(
                    "progress_percent",
                    if (total > 0) ((downloaded * 100) / total).toInt() else 0
                ).coerceIn(0, 100)
                val speedBps = obj.optLong("speed_bps", 0L)
                val speed = when {
                    speedBps > 0 -> formatRate(speedBps)
                    status == "queued" -> "Queued on server…"
                    status == "paused" -> "Paused on server"
                    status == "cancelling" -> "Cancelling…"
                    else -> "Preparing server download…"
                }

                transientFailures = 0
                when (status) {
                    "completed", "finished" -> {
                        updateTask(taskId) {
                            it.copy(
                                status = CloudDownloadStatus.COMPLETED,
                                progressPercent = 100,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speedText = "Completed • ${formatBytes(downloaded)}"
                            )
                        }
                        HttpServerRepository.notifyFilesChanged(obj.optString("destination", ""))
                        onComplete?.invoke()
                        return
                    }
                    "cancelled" -> {
                        updateTask(taskId) {
                            it.copy(status = CloudDownloadStatus.CANCELLED, progressPercent = percent,
                                downloadedBytes = downloaded, totalBytes = total, speedText = "Cancelled")
                        }
                        return
                    }
                    "failed", "error" -> {
                        updateTask(taskId) {
                            it.copy(status = CloudDownloadStatus.FAILED, progressPercent = percent,
                                downloadedBytes = downloaded, totalBytes = total,
                                errorMessage = obj.optString("error", "Server download failed"))
                        }
                        return
                    }
                    "paused" -> updateTask(taskId) {
                        it.copy(status = CloudDownloadStatus.PAUSED, progressPercent = percent,
                            downloadedBytes = downloaded, totalBytes = total, speedText = speed)
                    }
                    else -> updateTask(taskId) {
                        it.copy(status = if (status == "queued") CloudDownloadStatus.STORING_TO_SERVER else CloudDownloadStatus.DOWNLOADING,
                            progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, speedText = speed)
                    }
                }
            } catch (e: Exception) {
                transientFailures++
                updateTask(taskId) { it.copy(speedText = "Reconnecting to server…") }
                if (transientFailures >= 20) {
                    throw IllegalStateException("Server connection lost while reading download status")
                }
            }
        }
    }

    suspend fun syncFromServer() {
        if (!ServerConfig.isConfigured()) return
        try {
            val url = ServerConfig.baseUrl.value.trimEnd('/') + "/api/files/remote-download"
            val response = executeFast(authBuilder(url).get().build())
            val body = response.use { it.body?.string().orEmpty() }
            if (!response.isSuccessful) return
            val root = JSONObject(body)
            val data = root.opt("data")
            val arr = when (data) {
                is JSONArray -> data
                else -> JSONArray()
            }
            val serverTasks = mutableListOf<CloudDownloadTask>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("task_id")
                if (id.isBlank()) continue
                val status = when (o.optString("status").lowercase()) {
                    "completed" -> CloudDownloadStatus.COMPLETED
                    "failed" -> CloudDownloadStatus.FAILED
                    "cancelled" -> CloudDownloadStatus.CANCELLED
                    "paused" -> CloudDownloadStatus.PAUSED
                    "downloading" -> CloudDownloadStatus.DOWNLOADING
                    else -> CloudDownloadStatus.STORING_TO_SERVER
                }
                val bytes = o.optLong("downloaded_bytes")
                val total = o.optLong("total_bytes")
                val speed = o.optLong("speed_bps")
                serverTasks += CloudDownloadTask(
                    id = id,
                    url = o.optString("url"),
                    filename = o.optString("filename"),
                    destinationFolder = o.optString("destination"),
                    progressPercent = o.optInt("progress_percent", if (total > 0) ((bytes * 100) / total).toInt() else 0),
                    downloadedBytes = bytes,
                    totalBytes = total,
                    speedText = if (speed > 0) formatRate(speed) else status.name.replace('_', ' '),
                    status = status,
                    errorMessage = o.optString("error").takeIf { it.isNotBlank() },
                    timestamp = (o.optDouble("created_at", System.currentTimeMillis() / 1000.0) * 1000).toLong()
                )
                serverTaskIds[id] = id
            }
            _tasks.value = serverTasks
        } catch (e: Exception) {
            Log.w(TAG, "Server download sync failed: ${e.message}")
        }
    }

    fun cancelDownload(taskId: String) {
        sendControl(taskId, "cancel")
    }

    fun pauseDownload(taskId: String) {
        sendControl(taskId, "pause")
    }

    fun resumeDownload(taskId: String) {
        sendControl(taskId, "resume")
    }

    private fun sendControl(taskId: String, action: String) {
        scope.launch {
            val serverId = serverTaskIds[taskId] ?: taskId
            try {
                val payload = JSONObject().put("task_id", serverId).toString()
                val url = ServerConfig.baseUrl.value.trimEnd('/') + "/api/files/remote-download/$action"
                val response = executeFast(
                    authBuilder(url).header("Content-Type", "application/json")
                        .post(payload.toRequestBody("application/json".toMediaType())).build()
                )
                val body = response.use { it.body?.string().orEmpty() }
                if (!response.isSuccessful) throw IllegalStateException(parseError(body, "Server returned HTTP ${response.code}"))
                // Re-read server truth immediately instead of guessing local state.
                syncFromServer()
            } catch (e: Exception) {
                updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server control failed: ${e.message}") }
            }
        }
    }

    fun clearCompleted() {
        // Local UI cleanup only; the server database remains authoritative.
        _tasks.update { list -> list.filterNot {
            it.status == CloudDownloadStatus.COMPLETED ||
            it.status == CloudDownloadStatus.CANCELLED ||
            it.status == CloudDownloadStatus.FAILED
        } }
    }

    private fun updateTask(id: String, transform: (CloudDownloadTask) -> CloudDownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun parseError(body: String, fallback: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message", fallback) ?: fallback
    } catch (_: Exception) { fallback }

    private fun formatRate(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / 1048576.0)
        bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
        else -> "$bytes B/s"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
