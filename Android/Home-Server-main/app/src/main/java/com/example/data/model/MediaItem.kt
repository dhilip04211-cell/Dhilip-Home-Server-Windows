package com.example.data.model

enum class MediaCategory(val displayName: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    MUSIC("Music"),
    PHOTOS("Photos")
}

data class MediaItem(
    val id: String,
    val title: String,
    val category: MediaCategory,
    val year: Int,
    val duration: String,
    val genre: String,
    val rating: Double,
    val description: String,
    val posterGradientColor: Long = 0xFF1E293B,
    val backdropGradientColor: Long = 0xFF0F172A,
    val progress: Float = 0.0f,
    val isFavorite: Boolean = false,
    val isContinueWatching: Boolean = false,
    val isRecentlyAdded: Boolean = false,
    val isRecommended: Boolean = false,
    val resolution: String = "",
    val audioFormat: String = "",
    val fileSizeBytes: String = "",
    val streamUrl: String = "",
    val filePath: String = ""
)
