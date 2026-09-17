package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.FileType

@Composable
fun FileItemRow(
    fileItem: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null
) {
    val (icon, iconBgColor, iconTintColor, typeLabel) = when (fileItem.type) {
        FileType.FOLDER -> Quad(
            Icons.Default.Folder,
            Color(0xFF0284C7).copy(alpha = 0.15f),
            Color(0xFF0284C7),
            "Folder"
        )
        FileType.VIDEO -> Quad(
            Icons.Default.Movie,
            Color(0xFF8B5CF6).copy(alpha = 0.15f),
            Color(0xFF8B5CF6),
            "Video"
        )
        FileType.AUDIO -> Quad(
            Icons.Default.Audiotrack,
            Color(0xFFEC4899).copy(alpha = 0.15f),
            Color(0xFFEC4899),
            "Audio"
        )
        FileType.IMAGE -> Quad(
            Icons.Default.Image,
            Color(0xFF10B981).copy(alpha = 0.15f),
            Color(0xFF10B981),
            "Image"
        )
        FileType.PDF -> Quad(
            Icons.Default.PictureAsPdf,
            Color(0xFFEF4444).copy(alpha = 0.15f),
            Color(0xFFEF4444),
            "PDF"
        )
        FileType.DOCUMENT -> Quad(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            Color(0xFF3B82F6).copy(alpha = 0.15f),
            Color(0xFF3B82F6),
            "Document"
        )
        FileType.ARCHIVE -> Quad(
            Icons.Default.Archive,
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFF59E0B),
            "Archive"
        )
        FileType.OTHER -> Quad(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            Color(0xFF64748B).copy(alpha = 0.15f),
            Color(0xFF64748B),
            "File"
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("file_item_${fileItem.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = typeLabel,
                tint = iconTintColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTintColor,
                    fontWeight = FontWeight.Medium
                )

                if (fileItem.formattedSize != null) {
                    Text(
                        text = " • ${fileItem.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (fileItem.itemCount != null) {
                    Text(
                        text = " • ${fileItem.itemCount} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = " • ${fileItem.modifiedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (fileItem.isFolder) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        } else {
            IconButton(
                onClick = { onMoreClick?.invoke() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
