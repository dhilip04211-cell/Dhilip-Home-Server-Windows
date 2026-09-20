"""
DhilipHome Server - File Service
Secure filesystem management strictly confined within the configured MEDIA_ROOT.
Implements path traversal validation, MIME detection, and safe folder operations.
"""

import os
import shutil
import mimetypes
import uuid
from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
from werkzeug.utils import secure_filename
from app.utils.config import Config, format_bytes
from app.utils.security import is_safe_path


class FileService:
    """Handles secure file storage operations inside Config.MEDIA_ROOT."""

    @classmethod
    def resolve_safe_path(cls, relative_path: str = "") -> Optional[Path]:
        """
        Converts a user-supplied relative path to an absolute Path object
        and verifies it strictly stays inside Config.MEDIA_ROOT.
        Returns None if invalid or traversing outside.
        """
        clean_rel = relative_path.strip().lstrip("/\\")
        target = (Config.MEDIA_ROOT / clean_rel).resolve()

        if not is_safe_path(Config.MEDIA_ROOT, target):
            return None
        return target

    @classmethod
    def list_directory(cls, relative_path: str = "") -> Dict[str, Any]:
        """
        List items inside the specified directory relative to MEDIA_ROOT.
        Returns directories and files with complete metadata.
        """
        target = cls.resolve_safe_path(relative_path)
        if target is None or not target.exists():
            raise FileNotFoundError("Directory not found or path outside allowed media root")

        if not target.is_dir():
            raise NotADirectoryError("Target path is a file, not a directory")

        directories = []
        files = []

        for entry in os.scandir(target):
            try:
                stat = entry.stat()
                mtime_dt = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).isoformat()
                item_rel = Path(entry.path).relative_to(Config.MEDIA_ROOT).as_posix()

                if entry.is_dir(follow_symlinks=False):
                    directories.append({
                        "name": entry.name,
                        "path": item_rel,
                        "is_dir": True,
                        "modified_time": mtime_dt,
                        "modified_timestamp": stat.st_mtime,
                    })
                elif entry.is_file(follow_symlinks=False):
                    mime_type, _ = mimetypes.guess_type(entry.name)
                    ext = Path(entry.name).suffix.lower().lstrip(".")
                    files.append({
                        "name": entry.name,
                        "path": item_rel,
                        "is_dir": False,
                        "size_bytes": stat.st_size,
                        "size_human": format_bytes(stat.st_size),
                        "extension": ext,
                        "mime_type": mime_type or "application/octet-stream",
                        "modified_time": mtime_dt,
                        "modified_timestamp": stat.st_mtime,
                    })
            except (PermissionError, FileNotFoundError):
                continue

        # Sort alphabetically
        directories.sort(key=lambda d: d["name"].lower())
        files.sort(key=lambda f: f["name"].lower())

        current_rel = target.relative_to(Config.MEDIA_ROOT).as_posix()
        if current_rel == ".":
            current_rel = ""

        return {
            "current_path": current_rel,
            "total_directories": len(directories),
            "total_files": len(files),
            "directories": directories,
            "files": files,
        }

    @classmethod
    def get_item_info(cls, relative_path: str) -> Dict[str, Any]:
        """Retrieve detailed metadata for a single file or directory."""
        target = cls.resolve_safe_path(relative_path)
        if target is None or not target.exists():
            raise FileNotFoundError("File or directory not found")

        stat = target.stat()
        mtime_dt = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).isoformat()
        is_dir = target.is_dir()
        mime_type, _ = mimetypes.guess_type(target.name) if not is_dir else (None, None)
        ext = target.suffix.lower().lstrip(".") if not is_dir else None

        return {
            "name": target.name,
            "path": target.relative_to(Config.MEDIA_ROOT).as_posix(),
            "is_dir": is_dir,
            "size_bytes": stat.st_size if not is_dir else 0,
            "size_human": format_bytes(stat.st_size) if not is_dir else "Directory",
            "extension": ext,
            "mime_type": mime_type or ("inode/directory" if is_dir else "application/octet-stream"),
            "modified_time": mtime_dt,
            "modified_timestamp": stat.st_mtime,
        }

    @classmethod
    def create_folder(cls, parent_relative_path: str, folder_name: str) -> Dict[str, Any]:
        """Create a new folder safely under parent_relative_path."""
        safe_name = secure_filename(folder_name.strip())
        if not safe_name:
            raise ValueError("Invalid folder name")

        parent = cls.resolve_safe_path(parent_relative_path)
        if parent is None or not parent.is_dir():
            raise FileNotFoundError("Parent directory does not exist")

        new_folder = parent / safe_name
        if not is_safe_path(Config.MEDIA_ROOT, new_folder):
            raise ValueError("Invalid target folder destination")

        if new_folder.exists():
            raise FileExistsError(f"Folder '{safe_name}' already exists")

        new_folder.mkdir(parents=False, exist_ok=False)
        return cls.get_item_info(new_folder.relative_to(Config.MEDIA_ROOT).as_posix())

    @classmethod
    def save_uploaded_file(cls, parent_relative_path: str, file_storage) -> Dict[str, Any]:
        """Save an uploaded file safely inside parent directory."""
        if not file_storage or not file_storage.filename:
            raise ValueError("No file provided for upload")

        safe_name = secure_filename(file_storage.filename)
        if not safe_name:
            safe_name = f"upload_{int(datetime.now().timestamp())}.bin"

        parent = cls.resolve_safe_path(parent_relative_path)
        if parent is None or not parent.is_dir():
            raise FileNotFoundError("Target upload directory does not exist")

        dest_file = parent / safe_name
        if not is_safe_path(Config.MEDIA_ROOT, dest_file):
            raise ValueError("Destination path outside media root")

        # Write to a temporary file first so a failed/interrupted upload never
        # leaves a file that looks complete to clients. os.replace is atomic on
        # the same filesystem.
        temp_file = parent / f".{safe_name}.{uuid.uuid4().hex}.uploading"
        try:
            file_storage.save(str(temp_file))
            os.replace(str(temp_file), str(dest_file))
        except PermissionError as exc:
            try:
                temp_file.unlink(missing_ok=True)
            except Exception:
                pass
            raise PermissionError(
                f"Server storage is not writable: {parent}. Check MEDIA_ROOT permissions."
            ) from exc
        except Exception:
            try:
                temp_file.unlink(missing_ok=True)
            except Exception:
                pass
            raise

        return cls.get_item_info(dest_file.relative_to(Config.MEDIA_ROOT).as_posix())

    @classmethod
    def delete_item(cls, relative_path: str) -> bool:
        """Delete a file or directory safely."""
        target = cls.resolve_safe_path(relative_path)
        if target is None or not target.exists():
            raise FileNotFoundError("Target item not found")

        # Never allow deleting the root media folder itself
        if target == Config.MEDIA_ROOT:
            raise PermissionError("Cannot delete root media directory")

        if target.is_dir():
            shutil.rmtree(target)
        else:
            target.unlink()

        return True
