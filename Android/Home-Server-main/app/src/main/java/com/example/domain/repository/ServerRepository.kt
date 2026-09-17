package com.example.domain.repository

import com.example.data.model.FileItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.data.model.RecentActivity
import com.example.data.model.ServerStatus
import com.example.data.model.ServiceStatus
import com.example.data.model.StorageInfo
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun getServerStatus(): Flow<ServerStatus>
    fun getFiles(directoryPath: String = "/"): Flow<List<FileItem>>
    fun getMedia(category: MediaCategory = MediaCategory.ALL): Flow<List<MediaItem>>
    fun getMediaById(id: String): Flow<MediaItem?>
    fun getContinueWatching(): Flow<List<MediaItem>>
    fun getRecentlyAddedMedia(): Flow<List<MediaItem>>
    fun getFavoriteMedia(): Flow<List<MediaItem>>
    fun getServices(): Flow<List<ServiceStatus>>
    fun getUsers(): Flow<List<User>>
    fun getStorageInfo(): Flow<StorageInfo>
    fun getRecentActivities(): Flow<List<RecentActivity>>
    suspend fun toggleMediaFavorite(id: String): Boolean
    suspend fun deleteFile(path: String): Boolean
    suspend fun renameFile(path: String, newName: String): Boolean
    suspend fun createFolder(parentPath: String, name: String): Boolean
    suspend fun uploadFile(file: java.io.File, destinationFolder: String): Boolean
}
