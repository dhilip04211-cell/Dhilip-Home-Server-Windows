package com.example.network

import org.json.JSONObject

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(
        val code: String,
        val message: String,
        val statusCode: Int? = null
    ) : NetworkResult<Nothing>()
    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()

    companion object {
        const val CODE_SERVER_UNREACHABLE = "SERVER_UNREACHABLE"
        const val CODE_TIMEOUT = "TIMEOUT"
        const val CODE_AUTH_REQUIRED = "AUTH_REQUIRED"
        const val CODE_AUTH_FAILED = "AUTH_FAILED"
        const val CODE_FILE_NOT_FOUND = "FILE_NOT_FOUND"
        const val CODE_PERMISSION_DENIED = "PERMISSION_DENIED"
        const val CODE_SERVER_ERROR = "SERVER_ERROR"
        const val CODE_INVALID_RESPONSE = "INVALID_RESPONSE"
        const val CODE_NETWORK_CHANGED = "NETWORK_CHANGED"

        fun parseServerError(body: String?, statusCode: Int): Error {
            if (body.isNullOrBlank()) {
                val code = when (statusCode) {
                    401 -> CODE_AUTH_REQUIRED
                    403 -> CODE_PERMISSION_DENIED
                    404 -> CODE_FILE_NOT_FOUND
                    in 500..599 -> CODE_SERVER_ERROR
                    else -> CODE_SERVER_ERROR
                }
                return Error(
                    code = code,
                    message = "Server returned HTTP $statusCode",
                    statusCode = statusCode
                )
            }

            try {
                val json = JSONObject(body)
                if (json.has("error")) {
                    val errorObj = json.optJSONObject("error")
                    if (errorObj != null) {
                        val code = errorObj.optString("code", CODE_SERVER_ERROR)
                        val message = errorObj.optString("message", "Unknown server error")
                        return Error(code = code, message = message, statusCode = statusCode)
                    } else {
                        val msg = json.optString("error", "Server error")
                        return Error(code = CODE_SERVER_ERROR, message = msg, statusCode = statusCode)
                    }
                } else if (json.has("detail")) {
                    return Error(
                        code = CODE_SERVER_ERROR,
                        message = json.optString("detail"),
                        statusCode = statusCode
                    )
                } else if (json.has("message")) {
                    return Error(
                        code = CODE_SERVER_ERROR,
                        message = json.optString("message"),
                        statusCode = statusCode
                    )
                }
            } catch (_: java.lang.Exception) {
                // Not standard JSON error
            }

            return Error(
                code = CODE_SERVER_ERROR,
                message = "HTTP $statusCode: ${body.take(120)}",
                statusCode = statusCode
            )
        }
    }
}
