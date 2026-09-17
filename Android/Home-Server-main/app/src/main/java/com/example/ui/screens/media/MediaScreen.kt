package com.example.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.network.ApiClient
import com.example.ui.components.EmptyState
import com.example.ui.components.MediaCard
import kotlinx.coroutines.launch

@Composable
fun MediaScreen(viewModel: MediaViewModel, onMediaClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val admin by ApiClient.authInterceptor.isAdmin.collectAsStateWithLifecycle()
    var grid by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val filtered = items.filter { search.isBlank() || it.title.contains(search, true) }

    renameTarget?.let { item ->
        var value by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename media") }, text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Name") }) }, confirmButton = { Button(onClick = { viewModel.renameMedia(item, value) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Renamed" else "Rename failed") } }; renameTarget = null }) { Text("Rename") } }, dismissButton = { Button(onClick = { renameTarget = null }) { Text("Cancel") } })
    }
    deleteTarget?.let { item -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete media?") }, text = { Text("Delete ${item.title} permanently from the server? Administrator permission is required.") }, confirmButton = { Button(onClick = { deleteTarget = null; viewModel.deleteMedia(item) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Deleted" else "Delete failed or permission denied") } } }) { Text("Delete") } }, dismissButton = { Button(onClick = { deleteTarget = null }) { Text("Cancel") } }) }

    Scaffold(modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 90.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Media Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Server-indexed entertainment", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                        IconButton(onClick = { viewModel.refreshMedia() }) { if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "Refresh") }
                        IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "View") }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(.16f), MaterialTheme.colorScheme.surface))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f), placeholder = { Text("Search your library…") }, singleLine = true) }
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(MediaCategory.entries) { c -> FilterChip(selected = c == category, onClick = { viewModel.selectCategory(c) }, label = { Text(c.displayName) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) } }
                }
            }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("${filtered.size} items", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(if (admin) "Admin controls enabled" else "View mode", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } }
            if (filtered.isEmpty()) item { EmptyState("No media found", "Scan or upload media to your DhilipHome server.") }
            else if (grid) item {
                LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxWidth().height(((filtered.size + 1) / 2 * 240).coerceAtLeast(260).dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { media ->
                        BoxMedia(media, admin, { onMediaClick(media.id) }, { renameTarget = media }, { deleteTarget = media })
                    }
                }
            } else {
                items(filtered, key = { it.id }) { media -> MediaListRow(media, admin, { onMediaClick(media.id) }, { renameTarget = media }, { deleteTarget = media }) }
            }
        }
    }
}

@Composable private fun BoxMedia(item: MediaItem, admin: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) { Column(Modifier.fillMaxWidth()) { MediaCard(media = item, onClick = onOpen, modifier = Modifier.fillMaxWidth(), isCompact = false); if (admin) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename") }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) } } } }

@Composable private fun MediaListRow(item: MediaItem, admin: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(.78f)).clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${item.category.displayName} • ${item.fileSizeBytes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (admin) { IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename") }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) } } else { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
