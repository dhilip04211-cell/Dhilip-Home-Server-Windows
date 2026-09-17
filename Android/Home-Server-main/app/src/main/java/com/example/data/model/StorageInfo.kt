package com.example.data.model

data class StoragePartition(
    val mountPoint: String,
    val label: String,
    val filesystem: String,
    val totalGb: Double,
    val usedGb: Double,
    val usagePercent: Int
) {
    val usedPercent: Double get() = usagePercent.toDouble()
}

data class StorageInfo(
    val totalTb: Double = 0.0,
    val usedTb: Double = 0.0,
    val freeTb: Double = 0.0,
    val usagePercent: Int = 0,
    val partitions: List<StoragePartition> = emptyList()
)
