package com.example.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    private var sharedPreferences: SharedPreferences? = null
    @Volatile
    private var cachedToken: String? = null
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.sharedPreferences = prefs
        this.cachedToken = prefs.getString(KEY_AUTH_TOKEN, null)
        _isAdmin.value = decodeRole(this.cachedToken) == "admin"
    }

    fun setToken(token: String?) {
        cachedToken = token
        _isAdmin.value = decodeRole(token) == "admin"
        sharedPreferences?.edit()?.apply {
            if (token != null) {
                putString(KEY_AUTH_TOKEN, token)
            } else {
                remove(KEY_AUTH_TOKEN)
            }
            apply()
        }
    }

    fun getToken(): String? = cachedToken

    fun clear() {
        setToken(null)
    }

    private fun decodeRole(token: String?): String? {
        return try {
            val payload = token?.split(".")?.getOrNull(1) ?: return null
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val json = String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            JSONObject(json).optString("role", null)
        } catch (_: Exception) { null }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = cachedToken

        val newRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .build()
        } else {
            originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
        }

        return chain.proceed(newRequest)
    }

    companion object {
        private const val PREFS_NAME = "dhiliphome_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        const val USER_AGENT = "DhilipHome-Android/0.3.0"
    }
}
