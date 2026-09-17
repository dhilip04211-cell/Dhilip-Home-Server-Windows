package com.example.ui.screens.files

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileItem
import com.example.data.model.FileType
import com.example.data.repository.CloudDownloadManager
import com.example.network.ApiClient
import com.example.ui.components.ActiveCloudDownloadsCard
import com.example.ui.components.CloudDownloadDialog
import com.example.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onVideoClick: ((url: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val path by viewModel.currentPath.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sort by viewModel.sortOrder.collectAsStateWithLifecycle()
    val files by viewModel.displayedFiles.collectAsStateWithLifecycle()
    val downloads by viewModel.cloudDownloads.collectAsStateWithLifecycle()
    val isAdmin by ApiClient.authInterceptor.isAdmin.collectAsStateWithLifecycle()
    var grid by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var cloudDialog by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "upload.bin" } ?: "upload.bin"
                val file = java.io.File(context.cacheDir, "dhilip_upload_${System.currentTimeMillis()}_$name")
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                viewModel.uploadFile(file) { ok ->
                    scope.launch { snackbar.showSnackbar(if (ok) "Upload completed" else "Upload failed") }
                    file.delete()
                }
            } catch (e: Exception) { snackbar.showSnackbar("Upload failed: ${e.message}") }
        }
    }

    BackHandler(enabled = path != "/") { viewModel.navigateUp() }
    if (cloudDialog) CloudDownloadDialog(currentFolder = path, onDismiss = { cloudDialog = false }, onStartDownload = { url, filename, dest -> viewModel.startCloudDownload(url, filename, dest); cloudDialog = false })

    if (folderDialog) NameDialog("Create folder", "Folder name", "Create", { folderDialog = false }) { name -> viewModel.createFolder(name) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Folder created" else "Unable to create folder") } } }
    renameTarget?.let { target -> NameDialog("Rename item", "New name", "Rename", { renameTarget = null }, { name -> viewModel.renameFile(target.path, name) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Renamed" else "Rename failed") } } }) }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${target.name}?") }, text = { Text(if (target.isFolder) "This deletes the folder and its contents. This action is administrator-only." else "This permanently removes the file from the server.") }, confirmButton = { Button(onClick = { deleteTarget = null; viewModel.deleteFile(target.path) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Deleted" else "Delete failed or permission denied") } } }) { Text("Delete") } }, dismissButton = { Button(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }

    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }, floatingActionButton = {
        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(fabOpen) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    ActionChip("Cloud Download", Icons.Default.CloudDownload) { fabOpen = false; cloudDialog = true }
                    ActionChip("Upload File", Icons.Default.UploadFile) { fabOpen = false; uploadLauncher.launch(arrayOf("*/*")) }
                    ActionChip("New Folder", Icons.Default.CreateNewFolder) { fabOpen = false; folderDialog = true }
                }
            }
            FloatingActionButton(onClick = { fabOpen = !fabOpen }, containerColor = MaterialTheme.colorScheme.primary) { Icon(if (fabOpen) Icons.Default.Close else Icons.Default.Add, "Actions") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (path != "/") IconButton(onClick = viewModel::navigateUp) { Icon(Icons.Default.Close, "Back") }
                        Column(Modifier.weight(1f)) { Text("My Files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(path, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { search = !search }) { Icon(if (search) Icons.Default.Close else Icons.Default.Search, "Search") }
                        IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "View mode") }
                        Box { IconButton(onClick = { sortOpen = true }) { Icon(Icons.Default.Sort, "Sort") }; DropdownMenu(sortOpen, { sortOpen = false }) { FileSortOrder.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { viewModel.setSortOrder(option); sortOpen = false }) } } }
                    }
                    AnimatedVisibility(search) { OutlinedTextField(value = query, onValueChange = viewModel::setSearchQuery, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, placeholder = { Text("Search files…") }) }
                    Spacer(Modifier.height(10.dp))
                    GlassHeader("SERVER STORAGE", "${files.size} items • ${if (isAdmin) "Administrator" else "Standard user"}")
                }
            }
            if (downloads.isNotEmpty()) item { ActiveCloudDownloadsCard(tasks = downloads, onCancelTask = viewModel::cancelCloudDownload, onClearCompleted = viewModel::clearCompletedDownloads) }
            item { Spacer(Modifier.height(8.dp)) }
            if (files.isEmpty()) item { EmptyState("No files found", "The server returned no items for $path") }
            else if (grid) {
                item {
                    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxWidth().height(((files.size + 1) / 2 * 150).coerceAtLeast(180).dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(files, key = { it.id }) { file -> FileTile(file, isAdmin, { openFile(file, onVideoClick, context) }, { if (isAdmin) renameTarget = file }, { if (isAdmin) deleteTarget = file }) }
                    }
                }
            } else {
                items(files, key = { it.id }) { file -> FileRowPro(file, isAdmin, { openFile(file, onVideoClick, context) }, { if (isAdmin) renameTarget = file }, { if (isAdmin) deleteTarget = file }) }
            }
        }
    }
}

@Composable private fun GlassHeader(title: String, subtitle: String) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(.18f), MaterialTheme.colorScheme.surface.copy(.7f)))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) } }

@Composable private fun FileRowPro(file: FileItem, admin: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(.72f)).clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { FileGlyph(file.type, file.isFolder); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (file.isFolder) "Folder" else listOfNotNull(file.formattedSize, file.modifiedDate).joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Box { var open by remember { mutableStateOf(false) }; IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Actions") }; DropdownMenu(open, { open = false }) { DropdownMenuItem(text = { Text("Open") }, onClick = { open = false; onOpen() }); if (admin) { DropdownMenuItem(text = { Text("Edit / Rename") }, onClick = { open = false; onRename() }); DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; onDelete() }) } else DropdownMenuItem(text = { Text("Admin controls locked") }, onClick = { open = false }) } } } }

@Composable private fun FileTile(file: FileItem, admin: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(.78f)).clickable(onClick = onOpen).padding(12.dp)) { FileGlyph(file.type, file.isFolder, 42); Spacer(Modifier.height(8.dp)); Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(file.formattedSize ?: "Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { if (admin) { IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename") }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) } } } } }

@Composable private fun FileGlyph(type: FileType, folder: Boolean, size: Int = 34) { val icon = when { folder -> Icons.Default.Folder; type == FileType.VIDEO -> Icons.Default.VideoFile; type == FileType.AUDIO -> Icons.Default.AudioFile; type == FileType.IMAGE -> Icons.Default.Image; type == FileType.PDF || type == FileType.DOCUMENT -> Icons.Default.Description; else -> Icons.Default.Description }; Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size * .58).dp)) } }

@Composable private fun NameDialog(title: String, label: String, action: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var value by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) { Text(action) } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }) }

private fun openFile(file: FileItem, onVideoClick: ((String, String) -> Unit)?, context: android.content.Context) { if (file.isFolder) return; val url = file.downloadUrl ?: return; if (file.type == FileType.VIDEO && onVideoClick != null) onVideoClick(url, file.name) else { val mime = when (file.type) { FileType.AUDIO -> "audio/*"; FileType.IMAGE -> "image/*"; FileType.PDF -> "application/pdf"; else -> "*/*" }; runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), mime); addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } } }
