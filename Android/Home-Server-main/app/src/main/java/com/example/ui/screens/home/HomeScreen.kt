package com.example.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileType
import com.example.data.model.RecentActivity
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.MediaCard
import com.example.ui.components.MiniSystemDashboard
import com.example.ui.components.QuickAccessCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.ServerStatusCard
import com.example.ui.theme.StatusDemo

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFiles: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentActivities by viewModel.recentActivities.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Top Header section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DHILIP HOME",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (serverStatus.isOnline) "Connected to ${serverStatus.serverHostname}" else "Server: ${serverStatus.serverHostname.ifBlank { "Configure in Settings" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DemoModeBadge(text = if (serverStatus.isOnline) "ONLINE" else "OFFLINE")
                }
            }
        }

        // Mini System Dashboard (Storage, Memory, Temperature, System Status)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MiniSystemDashboard(
                    serverStatus = serverStatus,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshDashboard() }
                )
            }
        }

        // Quick Access section
        item {
            Spacer(modifier = Modifier.height(10.dp))
            SectionHeader(title = "Quick Access")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessCard(
                    title = "FILES",
                    subtitle = "Personal cloud",
                    icon = Icons.Default.Folder,
                    iconBgColor = Color(0xFF0284C7).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF0284C7),
                    onClick = onNavigateToFiles,
                    modifier = Modifier.weight(1f)
                )

                QuickAccessCard(
                    title = "MEDIA",
                    subtitle = "Movies & Music",
                    icon = Icons.Default.PlayCircle,
                    iconBgColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF8B5CF6),
                    onClick = onNavigateToMedia,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessCard(
                    title = "ADMIN",
                    subtitle = "Server console",
                    icon = Icons.Default.Dns,
                    iconBgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF10B981),
                    onClick = onNavigateToAdmin,
                    modifier = Modifier.weight(1f)
                )

                QuickAccessCard(
                    title = "SETTINGS",
                    subtitle = "Server config",
                    icon = Icons.Default.Settings,
                    iconBgColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFFF59E0B),
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Continue Watching Section
        if (continueWatching.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Continue Watching",
                    actionLabel = "See all",
                    onActionClick = onNavigateToMedia
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching, key = { it.id }) { media ->
                        MediaCard(
                            media = media,
                            onClick = { onMovieClick(media.id) }
                        )
                    }
                }
            }
        }

        // Recent Activity Section
        item {
            Spacer(modifier = Modifier.height(18.dp))
            SectionHeader(title = "Recent Activity")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                if (recentActivities.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No files accessed yet. Explore files in the FILES tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        recentActivities.forEachIndexed { index, activity ->
                            RecentActivityRow(activity = activity)
                            if (index < recentActivities.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(activity: RecentActivity) {
    val (icon, iconColor) = when (activity.fileType) {
        FileType.VIDEO -> Pair(Icons.Default.Movie, Color(0xFF8B5CF6))
        FileType.FOLDER -> Pair(Icons.Default.FolderOpen, Color(0xFF0284C7))
        FileType.AUDIO -> Pair(Icons.Default.MusicNote, Color(0xFFEC4899))
        FileType.IMAGE -> Pair(Icons.Default.Photo, Color(0xFF10B981))
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF64748B))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = activity.title,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = activity.typeName + (if (activity.sizeText != null) " • ${activity.sizeText}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = activity.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
