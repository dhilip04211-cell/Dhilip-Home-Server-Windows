package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ServerStatus
import com.example.data.model.StorageInfo
import com.example.data.repository.HttpServerRepository
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusWarning
import java.util.Locale

private enum class DetailDialogType {
    NONE, STORAGE, MEMORY, TEMPERATURE, CPU
}

@Composable
fun MiniSystemDashboard(
    serverStatus: ServerStatus,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var activeDialog by remember { mutableStateOf(DetailDialogType.NONE) }
    val storageInfo by HttpServerRepository.getStorageInfo().collectAsStateWithLifecycle(
        initialValue = StorageInfo(0.0, 0.0, 0.0, 0, emptyList())
    )

    val refreshRotation = if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_angle"
        ).value
    } else 0f

    // Storage formatting
    val storagePct = serverStatus.storageUsagePercent
    val storageFraction = (storagePct / 100f).coerceIn(0f, 1f)
    val storagePrimary: String
    val storageSecondary: String
    if (serverStatus.storageTotalTb >= 1.0) {
        val usedTbStr = String.format(Locale.US, "%.1f", serverStatus.storageUsedTb)
        val totalTbStr = String.format(Locale.US, "%.1f", serverStatus.storageTotalTb)
        val freeTbStr = String.format(Locale.US, "%.1f", serverStatus.storageFreeTb)
        storagePrimary = "$usedTbStr / $totalTbStr TB"
        storageSecondary = "$storagePct% Used • $freeTbStr TB Free"
    } else {
        val totalGb = serverStatus.storageTotalTb * 1024.0
        val usedGb = serverStatus.storageUsedTb * 1024.0
        val freeGb = (totalGb - usedGb).coerceAtLeast(0.0)
        storagePrimary = String.format(Locale.US, "%.1f / %.1f GB", usedGb, totalGb)
        storageSecondary = "$storagePct% Used • " + String.format(Locale.US, "%.1f GB Free", freeGb)
    }

    // Memory formatting
    val ramPct = serverStatus.ramUsagePercent
    val ramFraction = (ramPct / 100f).coerceIn(0f, 1f)
    val ramUsedStr = String.format(Locale.US, "%.1f", serverStatus.ramUsedGb)
    val ramTotalStr = String.format(Locale.US, "%.1f", serverStatus.ramTotalGb)
    val ramFreeStr = String.format(Locale.US, "%.1f", serverStatus.ramFreeGb)

    // Temperature formatting
    val temp = serverStatus.temperatureCelsius
    val tempColor = when {
        temp < 50 -> Color(0xFF06B6D4) // Cool cyan
        temp < 68 -> Color(0xFF10B981) // Normal green
        temp < 80 -> Color(0xFFF59E0B) // Warm amber
        else -> Color(0xFFEF4444)      // Hot red
    }
    val tempFraction = (temp / 100f).coerceIn(0f, 1f)
    val tempStatusLabel = when {
        temp < 50 -> "Cool"
        temp < 68 -> "Normal"
        temp < 80 -> "Warm"
        else -> "High Alert"
    }

    // CPU formatting
    val cpuPct = serverStatus.cpuPercent
    val cpuFraction = (cpuPct / 100f).coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mini_system_dashboard")
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: System Title, Host & Status Badge + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (serverStatus.isOnline) StatusRunning else StatusWarning)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = serverStatus.serverHostname.ifBlank { "System Telemetry" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = serverStatus.osVersion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (serverStatus.isOnline) StatusRunning.copy(alpha = 0.12f)
                                else StatusWarning.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (serverStatus.isOnline) "ONLINE" else "LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (serverStatus.isOnline) StatusRunning else StatusWarning
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh System Status",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(refreshRotation)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Helper hint text
            Text(
                text = "Tap any card below for full system diagnostics",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Storage & Memory (Clickable Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardTelemetryTile(
                    title = "STORAGE",
                    primaryValue = storagePrimary,
                    secondaryValue = storageSecondary,
                    icon = Icons.Default.Storage,
                    accentColor = Color(0xFF0284C7),
                    progress = storageFraction,
                    onClick = { activeDialog = DetailDialogType.STORAGE },
                    modifier = Modifier.weight(1f)
                )

                DashboardTelemetryTile(
                    title = "MEMORY (RAM)",
                    primaryValue = "$ramUsedStr / $ramTotalStr GB",
                    secondaryValue = "$ramPct% Used • $ramFreeStr GB Free",
                    icon = Icons.Default.Memory,
                    accentColor = Color(0xFF10B981),
                    progress = ramFraction,
                    onClick = { activeDialog = DetailDialogType.MEMORY },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Temperature & CPU Status (Clickable Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardTelemetryTile(
                    title = "TEMPERATURE",
                    primaryValue = "$temp°C",
                    secondaryValue = "Thermal: $tempStatusLabel",
                    icon = Icons.Default.Thermostat,
                    accentColor = tempColor,
                    progress = tempFraction,
                    onClick = { activeDialog = DetailDialogType.TEMPERATURE },
                    modifier = Modifier.weight(1f)
                )

                DashboardTelemetryTile(
                    title = "CPU LOAD",
                    primaryValue = "$cpuPct% Load",
                    secondaryValue = "${serverStatus.cpuCores} Cores • ${serverStatus.loadAverage.substringBefore(",")}",
                    icon = Icons.Default.Speed,
                    accentColor = Color(0xFF8B5CF6),
                    progress = cpuFraction,
                    onClick = { activeDialog = DetailDialogType.CPU },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom System Info Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Uptime",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Uptime: ${serverStatus.uptimeFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "IP: ${serverStatus.serverIp}:${serverStatus.serverPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Detail Dialogs
    when (activeDialog) {
        DetailDialogType.STORAGE -> {
            StorageDetailDialog(
                serverStatus = serverStatus,
                storageInfo = storageInfo,
                onDismiss = { activeDialog = DetailDialogType.NONE }
            )
        }
        DetailDialogType.MEMORY -> {
            MemoryDetailDialog(
                serverStatus = serverStatus,
                onDismiss = { activeDialog = DetailDialogType.NONE }
            )
        }
        DetailDialogType.TEMPERATURE -> {
            TemperatureDetailDialog(
                serverStatus = serverStatus,
                onDismiss = { activeDialog = DetailDialogType.NONE }
            )
        }
        DetailDialogType.CPU -> {
            CpuDetailDialog(
                serverStatus = serverStatus,
                onDismiss = { activeDialog = DetailDialogType.NONE }
            )
        }
        DetailDialogType.NONE -> {}
    }
}

@Composable
private fun DashboardTelemetryTile(
    title: String,
    primaryValue: String,
    secondaryValue: String,
    icon: ImageVector,
    accentColor: Color,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ==========================================
// 1. STORAGE DETAIL DIALOG
// ==========================================
@Composable
private fun StorageDetailDialog(
    serverStatus: ServerStatus,
    storageInfo: StorageInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Storage Pool & Volumes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Storage Usage Summary
                val totalGb = if (serverStatus.storageTotalTb >= 1.0) serverStatus.storageTotalTb * 1024.0 else storageInfo.totalTb * 1024.0
                val usedGb = if (serverStatus.storageTotalTb >= 1.0) serverStatus.storageUsedTb * 1024.0 else storageInfo.usedTb * 1024.0
                val freeGb = (totalGb - usedGb).coerceAtLeast(0.0)
                val pct = if (totalGb > 0) ((usedGb / totalGb) * 100).toInt() else serverStatus.storageUsagePercent

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.1f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Capacity",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (totalGb >= 1000) String.format(Locale.US, "%.2f TB", totalGb / 1024.0) else String.format(Locale.US, "%.1f GB", totalGb),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Used Space", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (usedGb >= 1000) String.format(Locale.US, "%.2f TB ($pct%%)", usedGb / 1024.0) else String.format(Locale.US, "%.1f GB ($pct%%)", usedGb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Available Space", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (freeGb >= 1000) String.format(Locale.US, "%.2f TB", freeGb / 1024.0) else String.format(Locale.US, "%.1f GB", freeGb),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                Text(
                    text = "Mounted Partitions & Disks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (storageInfo.partitions.isEmpty()) {
                    Text(
                        text = "1 Partition detected (Internal Flash Memory)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    storageInfo.partitions.forEach { part ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = part.mountPoint,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${part.usagePercent}% Used",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${part.filesystem} • ${String.format(Locale.US, "%.1f", part.usedGb)} / ${String.format(Locale.US, "%.1f", part.totalGb)} GB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { (part.usagePercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ==========================================
// 2. MEMORY (RAM) DETAIL DIALOG
// ==========================================
@Composable
private fun MemoryDetailDialog(
    serverStatus: ServerStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Memory (RAM) Diagnostics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val pct = serverStatus.ramUsagePercent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.1f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Physical RAM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "${String.format(Locale.US, "%.1f", serverStatus.ramTotalGb)} GB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "In Use (Active)", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${String.format(Locale.US, "%.1f", serverStatus.ramUsedGb)} GB ($pct%)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Free & Available", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${String.format(Locale.US, "%.1f", serverStatus.ramFreeGb)} GB", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF10B981)
                        )
                    }
                }

                if (serverStatus.swapTotalGb > 0.0) {
                    Text(
                        text = "Swap / Virtual Memory",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Swap Allocated", style = MaterialTheme.typography.bodySmall)
                        Text(text = "${String.format(Locale.US, "%.1f", serverStatus.swapUsedGb)} / ${String.format(Locale.US, "%.1f", serverStatus.swapTotalGb)} GB", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    text = "System Status: Low memory condition: false",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ==========================================
// 3. TEMPERATURE DETAIL DIALOG
// ==========================================
@Composable
private fun TemperatureDetailDialog(
    serverStatus: ServerStatus,
    onDismiss: () -> Unit
) {
    val temp = serverStatus.temperatureCelsius
    val tempF = (temp * 9 / 5) + 32
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Thermal & Cooling Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.1f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "$temp°C ($tempF°F)",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF59E0B)
                        )
                        Text(
                            text = "Thermal Rating: ${serverStatus.thermalStatus}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Thermal Zones Guide:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• < 50°C: Optimal / Idle Operating Temperature\n• 50°C - 68°C: Normal Server / Streaming Load\n• 68°C - 80°C: Heavy Transcoding or Intensive Operations\n• > 80°C: Thermal Protection Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ==========================================
// 4. CPU DETAIL DIALOG
// ==========================================
@Composable
private fun CpuDetailDialog(
    serverStatus: ServerStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Processor & Core Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.1f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Overall CPU Load", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "${serverStatus.cpuPercent}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        }
                        LinearProgressIndicator(
                            progress = { (serverStatus.cpuPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Processor Cores", style = MaterialTheme.typography.bodySmall)
                    Text(text = "${serverStatus.cpuCores} Physical Cores (${serverStatus.cpuLogicalCores} Threads)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Load Averages", style = MaterialTheme.typography.bodySmall)
                    Text(text = serverStatus.loadAverage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Active Tasks", style = MaterialTheme.typography.bodySmall)
                    Text(text = "${serverStatus.runningProcesses} running / ${serverStatus.totalProcesses} total", style = MaterialTheme.typography.bodySmall)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "System Environment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OS: ${serverStatus.osVersion}\nKernel: ${serverStatus.kernelVersion}\nUptime: ${serverStatus.uptimeFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
