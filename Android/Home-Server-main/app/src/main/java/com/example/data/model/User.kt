package com.example.data.model

enum class UserRole {
    ADMIN,
    USER
}

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val lastActive: String,
    val status: String = "Active"
)
