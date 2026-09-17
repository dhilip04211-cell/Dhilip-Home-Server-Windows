package com.example.data.model

enum class CloudDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    STORING_TO_SERVER,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class CloudDownloadTask(
    val id: String,
    val url: String,
    val filename: String,
    val destinationFolder: String,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = "",
    val status: CloudDownloadStatus = CloudDownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
