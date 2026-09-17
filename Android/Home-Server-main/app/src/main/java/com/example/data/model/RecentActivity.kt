package com.example.data.model

data class RecentActivity(
    val id: String,
    val title: String,
    val typeName: String,
    val timestamp: String,
    val fileType: FileType,
    val sizeText: String? = null
)
