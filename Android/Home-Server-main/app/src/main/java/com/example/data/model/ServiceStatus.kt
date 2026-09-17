package com.example.data.model

enum class ServiceState(val label: String) {
    RUNNING("Running"),
    STOPPED("Stopped"),
    RESTARTING("Restarting"),
    UNKNOWN("Unknown")
}

data class ServiceStatus(
    val name: String,
    val serviceUnit: String,
    val state: ServiceState,
    val port: Int? = null,
    val description: String,
    val uptime: String = "Unavailable",
    val memoryUsageMb: Int = 0
)
