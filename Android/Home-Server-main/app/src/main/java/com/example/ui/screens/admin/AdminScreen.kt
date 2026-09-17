package com.example.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ServiceState
import com.example.data.model.ServiceStatus
import com.example.data.model.StorageInfo
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.components.DashboardMetric
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.ServiceStatusCard
import com.example.ui.components.StorageCard
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStopped
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch
import java.util.Locale

private enum class AdminActiveModal {
    NONE,
    SERVER_MODAL,
    SERVICES_MODAL,
    STORAGE_MODAL,
    USERS_MODAL,
    BACKUPS_MODAL,
    LOGS_MODAL,
    SECURITY_MODAL,
    CONFIRM_REBOOT,
    CONFIRM_SHUTDOWN,
    CONFIRM_RESTART_SVC
}

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val systemLogs by viewModel.systemLogs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeModal by remember { mutableStateOf(AdminActiveModal.NONE) }
    var selectedServiceForAction by remember { mutableStateOf<ServiceStatus?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }

    // Dialog: Confirmation for Restarting Services
    if (activeModal == AdminActiveModal.CONFIRM_RESTART_SVC) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Restart All Daemons") },
            text = { Text("Are you sure you want to restart all running backend services? Telemetry streams will reconnect immediately.") },
            confirmButton = {
                Button(
                    onClick = {
                        activeModal = AdminActiveModal.NONE
                        viewModel.restartServices("all")
                        scope.launch { snackbarHostState.showSnackbar("All services restarted successfully.") }
                    }
                ) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Confirmation for Reboot
    if (activeModal == AdminActiveModal.CONFIRM_REBOOT) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Reboot Server") },
            text = { Text("Rebooting will gracefully restart the host system and all associated services.") },
            confirmButton = {
                Button(
                    onClick = {
                        activeModal = AdminActiveModal.NONE
                        viewModel.rebootServer()
                        scope.launch { snackbarHostState.showSnackbar("Reboot initiated. System returning online...") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusWarning)
                ) {
                    Text("Confirm Reboot", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Confirmation for Shutdown
    if (activeModal == AdminActiveModal.CONFIRM_SHUTDOWN) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Shutdown Server") },
            text = { Text("Shutting down will stop all services and put the machine into power-saving standby mode.") },
            confirmButton = {
                Button(
                    onClick = {
                        activeModal = AdminActiveModal.NONE
                        viewModel.shutdownServer()
                        scope.launch { snackbarHostState.showSnackbar("Server successfully halted.") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusStopped)
                ) {
                    Text("Shutdown Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Single Service Action
    if (selectedServiceForAction != null) {
        val svc = selectedServiceForAction!!
        AlertDialog(
            onDismissRequest = { selectedServiceForAction = null },
            title = { Text("Manage ${svc.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unit: ${svc.serviceUnit}")
                    Text("State: ${svc.state.name}")
                    Text("Port: ${svc.port}")
                    Text("Memory: ${svc.memoryUsageMb} MB")
                    Text("Uptime: ${svc.uptime}")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.toggleService(svc.serviceUnit)
                            val act = if (svc.state == ServiceState.RUNNING) "stopped" else "started"
                            scope.launch { snackbarHostState.showSnackbar("${svc.name} $act.") }
                            selectedServiceForAction = null
                        }
                    ) {
                        Text(if (svc.state == ServiceState.RUNNING) "Stop" else "Start")
                    }
                    Button(
                        onClick = {
                            viewModel.restartServices(svc.serviceUnit)
                            scope.launch { snackbarHostState.showSnackbar("${svc.name} restarting...") }
                            selectedServiceForAction = null
                        }
                    ) {
                        Text("Restart")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedServiceForAction = null }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header: Server Admin
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Server Admin",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Console: ${serverStatus.serverHostname}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DemoModeBadge(text = if (serverStatus.isOnline) "ONLINE" else "OFFLINE")
                    }
                }
            }

            // Quick Power & Maintenance Action Buttons (Fully Functional)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { activeModal = AdminActiveModal.CONFIRM_RESTART_SVC },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_restart_service"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Restart Svc",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = { activeModal = AdminActiveModal.CONFIRM_REBOOT },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_restart_server"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusWarning
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reboot",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarning
                        )
                    }

                    OutlinedButton(
                        onClick = { activeModal = AdminActiveModal.CONFIRM_SHUTDOWN },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_shutdown_server"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusStopped
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shutdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusStopped
                        )
                    }
                }
            }

            // Live Telemetry Grid: CPU, RAM, Storage, Temperature, Uptime, Network
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "SERVER TELEMETRY")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetric(
                            label = "Service Port",
                            value = "${serverStatus.serverPort}",
                            subValue = "HTTP Fileserver",
                            icon = Icons.Default.Dns,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetric(
                            label = "System State",
                            value = if (serverStatus.isOnline) "Active" else "Offline",
                            subValue = serverStatus.statusText,
                            icon = Icons.Default.Memory,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = if (serverStatus.isOnline) StatusRunning else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetric(
                            label = "Host Target",
                            value = serverStatus.serverHostname.ifBlank { if (serverStatus.isOnline) "Server" else "Offline" },
                            subValue = "${serverStatus.serverIp}",
                            icon = Icons.Default.Storage,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetric(
                            label = "Network Telemetry",
                            value = "${serverStatus.cpuPercent}% CPU Load",
                            subValue = serverStatus.networkStatus,
                            icon = Icons.Default.Thermostat,
                            progressFraction = (serverStatus.cpuPercent / 100f).coerceIn(0f, 1f),
                            accentColor = if (serverStatus.isOnline) StatusRunning else StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Services Status Cards (Interactive click to start/stop/restart)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Active Services",
                    actionLabel = "${services.count { it.state == ServiceState.RUNNING }}/${services.size} running"
                )
            }

            items(services, key = { it.serviceUnit }) { service ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ServiceStatusCard(
                        service = service,
                        onServiceClick = {
                            selectedServiceForAction = service
                        }
                    )
                }
            }

            // Administration Sections (Section 7 - ALL ENABLED)
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(title = "Administration Modules")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminModuleCard(
                        title = "SERVER HARDWARE",
                        subtitle = "Hardware: ${serverStatus.cpuCores} Cores • ${serverStatus.osVersion} • ${serverStatus.temperatureCelsius}°C",
                        icon = Icons.Default.Dns,
                        onClick = { activeModal = AdminActiveModal.SERVER_MODAL }
                    )

                    AdminModuleCard(
                        title = "SERVICES MANAGER",
                        subtitle = "Manage & control all ${services.size} system daemons and protocols",
                        icon = Icons.Default.Widgets,
                        onClick = { activeModal = AdminActiveModal.SERVICES_MODAL }
                    )

                    AdminModuleCard(
                        title = "STORAGE POOL",
                        subtitle = "Pool: ${String.format(Locale.US, "%.1f", if (serverStatus.storageTotalTb >= 1.0) serverStatus.storageTotalTb else serverStatus.storageTotalTb * 1024.0)} ${if (serverStatus.storageTotalTb >= 1.0) "TB" else "GB"} • Mounts & filesystems",
                        icon = Icons.Default.Storage,
                        onClick = { activeModal = AdminActiveModal.STORAGE_MODAL }
                    )

                    AdminModuleCard(
                        title = "USER ACCOUNTS",
                        subtitle = "${users.size} Accounts • Roles & Access Management",
                        icon = Icons.Default.Group,
                        onClick = { activeModal = AdminActiveModal.USERS_MODAL }
                    )

                    AdminModuleCard(
                        title = "SYSTEM BACKUPS",
                        subtitle = "${backups.size} Archives • Create snapshots & restore data",
                        icon = Icons.Default.Backup,
                        onClick = { activeModal = AdminActiveModal.BACKUPS_MODAL }
                    )

                    AdminModuleCard(
                        title = "SYSTEM LOGS",
                        subtitle = "Live events: systemd journal, access audit & downlod telemetry",
                        icon = Icons.Default.Description,
                        onClick = { activeModal = AdminActiveModal.LOGS_MODAL }
                    )

                    AdminModuleCard(
                        title = "SECURITY & NETWORK",
                        subtitle = "Tailscale Mesh VPN • Firewall Active • SSH Port 22",
                        icon = Icons.Default.Security,
                        onClick = { activeModal = AdminActiveModal.SECURITY_MODAL }
                    )
                }
            }

            // Storage Details Card
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(title = "Storage Overview")
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StorageCard(storageInfo = storageInfo)
                }
            }
        }
    }

    // ==========================================
    // ALL ADMIN MODALS IMPLEMENTATION
    // ==========================================

    // 1. SERVER HARDWARE MODAL
    if (activeModal == AdminActiveModal.SERVER_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Server Hardware Specs", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecRow("Hostname", serverStatus.serverHostname)
                    SpecRow("Operating System", serverStatus.osVersion)
                    SpecRow("Kernel", serverStatus.kernelVersion)
                    SpecRow("Processor Cores", "${serverStatus.cpuCores} Physical (${serverStatus.cpuLogicalCores} Threads)")
                    SpecRow("CPU Frequency", "${serverStatus.cpuFrequencyMhz.toInt()} MHz")
                    SpecRow("Current CPU Load", "${serverStatus.cpuPercent}% (${serverStatus.loadAverage})")
                    SpecRow("Physical Memory", "${String.format(Locale.US, "%.1f", serverStatus.ramUsedGb)} / ${String.format(Locale.US, "%.1f", serverStatus.ramTotalGb)} GB (${serverStatus.ramUsagePercent}%)")
                    SpecRow("Thermal Sensor", "${serverStatus.temperatureCelsius}°C (${serverStatus.thermalStatus})")
                    SpecRow("System Uptime", serverStatus.uptimeFormatted)
                    SpecRow("Local Server IP", "${serverStatus.serverIp}:${serverStatus.serverPort}")
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Done") }
            }
        )
    }

    // 2. SERVICES MANAGER MODAL
    if (activeModal == AdminActiveModal.SERVICES_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Services Manager (${services.size})", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    services.forEach { svc ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = svc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "${svc.serviceUnit} • Port ${svc.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (svc.state == ServiceState.RUNNING) StatusRunning.copy(alpha = 0.15f) else StatusStopped.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = svc.state.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (svc.state == ServiceState.RUNNING) StatusRunning else StatusStopped
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.toggleService(svc.serviceUnit)
                                            val act = if (svc.state == ServiceState.RUNNING) "stopped" else "started"
                                            scope.launch { snackbarHostState.showSnackbar("${svc.name} $act.") }
                                        }
                                    ) {
                                        Text(if (svc.state == ServiceState.RUNNING) "Stop" else "Start")
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.restartServices(svc.serviceUnit)
                                            scope.launch { snackbarHostState.showSnackbar("${svc.name} restarted.") }
                                        }
                                    ) {
                                        Text("Restart")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Close") }
            }
        )
    }

    // 3. STORAGE POOL MODAL
    if (activeModal == AdminActiveModal.STORAGE_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Storage Volume Manager", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val totalGb = if (serverStatus.storageTotalTb >= 1.0) serverStatus.storageTotalTb * 1024.0 else storageInfo.totalTb * 1024.0
                    val usedGb = if (serverStatus.storageTotalTb >= 1.0) serverStatus.storageUsedTb * 1024.0 else storageInfo.usedTb * 1024.0
                    val freeGb = (totalGb - usedGb).coerceAtLeast(0.0)

                    Text(text = "Total System Capacity: ${String.format(Locale.US, "%.1f GB", totalGb)}", fontWeight = FontWeight.Bold)
                    Text(text = "Used: ${String.format(Locale.US, "%.1f GB", usedGb)} • Free: ${String.format(Locale.US, "%.1f GB", freeGb)}")

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(text = "Mounted File Systems:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (storageInfo.partitions.isEmpty()) {
                        Text(text = "No mounted partitions reported by server", style = MaterialTheme.typography.bodySmall)
                    } else {
                        storageInfo.partitions.forEach { p ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(text = "${p.mountPoint} (${p.filesystem})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(text = "${String.format(Locale.US, "%.1f", p.usedGb)} / ${String.format(Locale.US, "%.1f", p.totalGb)} GB (${p.usagePercent}% used)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Close") }
            }
        )
    }

    // 4. USERS MODAL
    if (activeModal == AdminActiveModal.USERS_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("User Accounts (${users.size})", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showAddUserDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add User", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    users.forEach { u ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = u.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "@${u.username} • Role: ${u.role.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (u.role != UserRole.ADMIN) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteUser(u.id)
                                            scope.launch { snackbarHostState.showSnackbar("User ${u.username} removed.") }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "ADMIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Done") }
            }
        )
    }

    // Sub-dialog: Add User
    if (showAddUserDialog) {
        var newUsername by remember { mutableStateOf("") }
        var newDisplayName by remember { mutableStateOf("") }
        var isAdminRole by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Create New User Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grant Admin Privileges")
                        androidx.compose.material3.Switch(
                            checked = isAdminRole,
                            onCheckedChange = { isAdminRole = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsername.isNotBlank()) {
                            viewModel.addUser(
                                username = newUsername,
                                displayName = newDisplayName,
                                role = if (isAdminRole) UserRole.ADMIN else UserRole.USER
                            )
                            scope.launch { snackbarHostState.showSnackbar("User $newUsername created successfully!") }
                            showAddUserDialog = false
                        }
                    }
                ) {
                    Text("Add User")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 5. BACKUPS MODAL
    if (activeModal == AdminActiveModal.BACKUPS_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("System Backups", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            viewModel.createBackupNow()
                            scope.launch { snackbarHostState.showSnackbar("Backup creation started.") }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Now", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Storage Destination: /var/backups/dhiliphome", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    backups.forEach { bk ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = bk.filename, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(text = "${bk.size} • ${bk.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteBackup(bk.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Done") }
            }
        )
    }

    // 6. SYSTEM LOGS MODAL
    if (activeModal == AdminActiveModal.LOGS_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Real-Time Event Logs", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        systemLogs.forEach { log ->
                            Text(
                                text = log,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Close") }
            }
        )
    }

    // 7. SECURITY & NETWORK MODAL
    if (activeModal == AdminActiveModal.SECURITY_MODAL) {
        AlertDialog(
            onDismissRequest = { activeModal = AdminActiveModal.NONE },
            title = { Text("Security & Firewall Status", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecRow("Uncomplicated Firewall (UFW)", "Active (ALLOW 8080, 8000, 445, 22)")
                    SpecRow("SSH Remote Root", "Disabled (Key-based auth enforced)")
                    SpecRow("Tailscale Overlay", "Connected (100.84.21.11 / Encrypted)")
                    SpecRow("Samba CIFS/SMB", "Restricted to Local Subnet")
                    SpecRow("SSL/TLS Encryption", "Active (HTTPS / WSS ready)")
                }
            },
            confirmButton = {
                TextButton(onClick = { activeModal = AdminActiveModal.NONE }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AdminModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("admin_module_${title.lowercase().replace(' ', '_')}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
