"""
DhilipHome Server - Media Service
Handles media scanning, automatic classification, SQLite indexing, and HTTP Range streaming.
"""

import os
import mimetypes
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Generator, Tuple
from app.utils.config import Config, format_bytes
from app.utils.security import is_safe_path
from app.database.models import MediaModel

# Classification extensions
MEDIA_EXTENSIONS = {
    "Music": {".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".wma", ".opus", ".alac", ".aiff"},
    "Images": {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".tiff", ".ico"},
    "Documents": {".pdf", ".txt", ".doc", ".docx", ".epub", ".mobi", ".md", ".rtf", ".odt", ".csv"},
    "Videos": {".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv", ".ts", ".m4v", ".3gp", ".vob"},
}


def classify_media_file(filename: str, relative_path: str) -> str:
    """Categorize file based on extension and directory conventions."""
    ext = Path(filename).suffix.lower()
    lower_path = relative_path.lower()

    if ext in MEDIA_EXTENSIONS["Videos"]:
        if "movie" in lower_path or "films" in lower_path:
            return "Movies"
        if "tv" in lower_path or "series" in lower_path or "shows" in lower_path:
            return "TV"
        return "Videos"

    if ext in MEDIA_EXTENSIONS["Music"]:
        return "Music"

    if ext in MEDIA_EXTENSIONS["Images"]:
        return "Images"

    if ext in MEDIA_EXTENSIONS["Documents"]:
        return "Documents"

    return "Other"


class MediaService:
    """Service for media scanning, SQLite indexing, and chunked streaming."""

    @classmethod
    def scan_and_index(cls, progress_callback=None) -> Dict[str, Any]:
        """
        Recursively scan MEDIA_ROOT, classify all files, and update SQLite media_files table.
        Emits progress_callback(scanned, current_file) if provided.
        """
        Config.MEDIA_ROOT.mkdir(parents=True, exist_ok=True)

        scanned_count = 0
        active_paths = []
        category_counts = {}

        for root, _, files in os.walk(Config.MEDIA_ROOT):
            for filename in files:
                full_path = Path(root) / filename
                if not is_safe_path(Config.MEDIA_ROOT, full_path):
                    continue

                try:
                    stat = full_path.stat()
                    rel_path = full_path.relative_to(Config.MEDIA_ROOT).as_posix()
                    category = classify_media_file(filename, rel_path)
                    ext = full_path.suffix.lower().lstrip(".")
                    mime, _ = mimetypes.guess_type(filename)
                    mime_type = mime or "application/octet-stream"

                    MediaModel.upsert(
                        filename=filename,
                        relative_path=rel_path,
                        category=category,
                        extension=ext,
                        mime_type=mime_type,
                        size_bytes=stat.st_size,
                        modified_time=stat.st_mtime,
                    )

                    active_paths.append(rel_path)
                    scanned_count += 1
                    category_counts[category] = category_counts.get(category, 0) + 1

                    if progress_callback and scanned_count % 10 == 0:
                        progress_callback(scanned_count, filename)

                except (PermissionError, FileNotFoundError):
                    continue

        # Purge deleted files from database
        try:
            MediaModel.delete_missing(active_paths)
        except Exception:
            pass

        return {
            "total_scanned": scanned_count,
            "categories": category_counts,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    @classmethod
    def list_media(cls, category: Optional[str] = None) -> Dict[str, Any]:
        """List indexed media items, optionally filtered by category."""
        items = MediaModel.search(query="", category=category, limit=2000)
        formatted = []
        for it in items:
            formatted.append({
                "id": it["id"],
                "filename": it["filename"],
                "path": it["relative_path"],
                "category": it["category"],
                "extension": it["extension"],
                "mime_type": it["mime_type"],
                "size_bytes": it["size_bytes"],
                "size_human": format_bytes(it["size_bytes"]),
                "modified_time": datetime.fromtimestamp(it["modified_time"], tz=timezone.utc).isoformat() if it["modified_time"] else None,
                "stream_url": f"/api/media/stream/{it['relative_path']}",
            })

        return {
            "count": len(formatted),
            "category_summary": MediaModel.get_category_counts(),
            "items": formatted,
        }

    @classmethod
    def search_media(cls, query: str, category: Optional[str] = None) -> List[Dict[str, Any]]:
        """Search media by filename query."""
        results = MediaModel.search(query=query, category=category, limit=200)
        formatted = []
        for it in results:
            formatted.append({
                "id": it["id"],
                "filename": it["filename"],
                "path": it["relative_path"],
                "category": it["category"],
                "extension": it["extension"],
                "mime_type": it["mime_type"],
                "size_bytes": it["size_bytes"],
                "size_human": format_bytes(it["size_bytes"]),
                "modified_time": datetime.fromtimestamp(it["modified_time"], tz=timezone.utc).isoformat() if it["modified_time"] else None,
                "stream_url": f"/api/media/stream/{it['relative_path']}",
            })
        return formatted

    @classmethod
    def prepare_range_stream(
        cls, relative_path: str, range_header: Optional[str] = None, chunk_size: int = 1024 * 512
    ) -> Tuple[Optional[Generator[bytes, None, None]], int, int, int, str]:
        """
        Validate file path and prepare byte stream supporting HTTP 206 Partial Content.
        Returns: (chunk_generator, start_byte, end_byte, total_size, mime_type)
        """
        clean_rel = relative_path.strip().lstrip("/\\")
        target = (Config.MEDIA_ROOT / clean_rel).resolve()

        if not is_safe_path(Config.MEDIA_ROOT, target) or not target.is_file():
            raise FileNotFoundError("Media file not found or path traversal detected")

        total_size = target.stat().st_size
        mime, _ = mimetypes.guess_type(target.name)
        mime_type = mime or "application/octet-stream"

        start = 0
        end = total_size - 1

        if range_header and range_header.startswith("bytes="):
            parts = range_header[6:].split("-")
            if parts[0]:
                start = int(parts[0])
            if len(parts) > 1 and parts[1]:
                end = int(parts[1])

        # Clamp boundaries
        start = max(0, min(start, total_size - 1))
        end = max(start, min(end, total_size - 1))
        length = (end - start) + 1

        def stream_generator():
            with open(target, "rb") as f:
                f.seek(start)
                bytes_left = length
                while bytes_left > 0:
                    read_len = min(chunk_size, bytes_left)
                    data = f.read(read_len)
                    if not data:
                        break
                    bytes_left -= len(data)
                    yield data

        return stream_generator(), start, end, total_size, mime_type
