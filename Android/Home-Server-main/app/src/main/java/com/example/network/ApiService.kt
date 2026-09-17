package com.example.network

import com.example.network.models.AuthLoginRequest
import com.example.network.models.AuthResponse
import com.example.network.models.AuthStatusResponse
import com.example.network.models.CreateFolderRequest
import com.example.network.models.DiscoveryResponse
import com.example.network.models.HealthResponse
import com.example.network.models.NetworkInfoResponse
import com.example.network.models.ServerInfoResponse
import com.example.network.models.SystemInfoResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    @GET("api/health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("api/discovery")
    suspend fun getDiscovery(): Response<DiscoveryResponse>

    @GET("api/server")
    suspend fun getServerInfo(): Response<ServerInfoResponse>

    @GET("api/system")
    suspend fun getSystemInfo(): Response<SystemInfoResponse>

    @GET("api/storage")
    suspend fun getStorage(): Response<ResponseBody>

    @GET("api/network")
    suspend fun getNetwork(): Response<NetworkInfoResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthLoginRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ResponseBody>

    @GET("api/auth/status")
    suspend fun getAuthStatus(): Response<AuthStatusResponse>

    @GET("api/files")
    suspend fun getFiles(@Query("path") path: String = ""): Response<ResponseBody>

    @GET("api/files/list")
    suspend fun listFiles(@Query("path") path: String = ""): Response<ResponseBody>

    @GET("api/files/info")
    suspend fun getFileInfo(@Query("path") path: String): Response<ResponseBody>

    @Streaming
    @GET("api/files/download")
    suspend fun downloadFile(@Query("path") path: String): Response<ResponseBody>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("path") destinationFolder: RequestBody
    ): Response<ResponseBody>

    @POST("api/files/folder")
    suspend fun createFolder(@Body request: CreateFolderRequest): Response<ResponseBody>

    @DELETE("api/files")
    suspend fun deleteFile(@Query("path") path: String): Response<ResponseBody>

    @POST("api/files/rename")
    suspend fun renameFile(@Body request: RenameFileRequest): Response<ResponseBody>

    @POST("api/files/remote-download/cancel")
    suspend fun cancelRemoteDownload(@Body request: RemoteDownloadCancelRequest): Response<ResponseBody>

    @GET("api/media")
    suspend fun getMedia(@Query("category") category: String? = null): Response<ResponseBody>

    @GET("api/media/search")
    suspend fun searchMedia(
        @Query("q") query: String,
        @Query("category") category: String? = null
    ): Response<ResponseBody>

    @POST("api/media/scan")
    suspend fun triggerMediaScan(): Response<ResponseBody>

    @GET("api/services")
    suspend fun getServices(): Response<ResponseBody>

    @GET("api/users")
    suspend fun getUsers(): Response<ResponseBody>

    @GET("api/activity")
    suspend fun getActivity(): Response<ResponseBody>
}
