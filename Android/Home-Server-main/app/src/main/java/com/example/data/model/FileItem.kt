package com.example.data.model

enum class FileType {
    FOLDER,
    VIDEO,
    AUDIO,
    IMAGE,
    PDF,
    DOCUMENT,
    ARCHIVE,
    OTHER
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val type: FileType,
    val sizeBytes: Long? = null,
    val formattedSize: String? = null,
    val modifiedDate: String,
    val itemCount: Int? = null,
    val downloadUrl: String? = null
)
