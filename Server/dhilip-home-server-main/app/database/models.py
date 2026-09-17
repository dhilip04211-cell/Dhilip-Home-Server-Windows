"""
DhilipHome Server - Database Models
Encapsulates database operations for users, media files, settings, and server metadata.
"""

import time
from typing import Optional, List, Dict, Any
from app.database.database import get_db_connection


class UserModel:
    """User account operations."""

    @staticmethod
    def get_by_username(username: str) -> Optional[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
            row = cursor.fetchone()
            return dict(row) if row else None

    @staticmethod
    def get_by_id(user_id: int) -> Optional[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, username, role, created_at, last_login FROM users WHERE id = ?", (user_id,))
            row = cursor.fetchone()
            return dict(row) if row else None

    @staticmethod
    def update_last_login(user_id: int):
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?", (user_id,))

    @staticmethod
    def list_all() -> List[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, username, role, created_at, last_login FROM users ORDER BY id ASC")
            return [dict(r) for r in cursor.fetchall()]


class MediaModel:
    """Media library database operations."""

    @staticmethod
    def upsert(filename: str, relative_path: str, category: str, extension: str, mime_type: str, size_bytes: int, modified_time: float):
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO media_files (filename, relative_path, category, extension, mime_type, size_bytes, modified_time, indexed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(relative_path) DO UPDATE SET
                    filename=excluded.filename,
                    category=excluded.category,
                    extension=excluded.extension,
                    mime_type=excluded.mime_type,
                    size_bytes=excluded.size_bytes,
                    modified_time=excluded.modified_time,
                    indexed_at=CURRENT_TIMESTAMP;
            """, (filename, relative_path, category, extension, mime_type, size_bytes, modified_time))

    @staticmethod
    def search(query: str, category: Optional[str] = None, limit: int = 100) -> List[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            term = f"%{query}%"
            if category and category.lower() != "all":
                cursor.execute("""
                    SELECT * FROM media_files
                    WHERE (filename LIKE ? OR relative_path LIKE ?) AND category = ?
                    ORDER BY modified_time DESC LIMIT ?
                """, (term, term, category, limit))
            else:
                cursor.execute("""
                    SELECT * FROM media_files
                    WHERE filename LIKE ? OR relative_path LIKE ?
                    ORDER BY modified_time DESC LIMIT ?
                """, (term, term, limit))
            return [dict(r) for r in cursor.fetchall()]

    @staticmethod
    def get_by_path(relative_path: str) -> Optional[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM media_files WHERE relative_path = ?", (relative_path,))
            row = cursor.fetchone()
            return dict(row) if row else None

    @staticmethod
    def get_category_counts() -> Dict[str, int]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT category, COUNT(*) as count FROM media_files GROUP BY category")
            counts = {row["category"]: row["count"] for row in cursor.fetchall()}
            return counts

    @staticmethod
    def delete_missing(active_relative_paths: List[str]):
        """Remove indexed records for files that have been deleted from disk."""
        if not active_relative_paths:
            with get_db_connection() as conn:
                conn.execute("DELETE FROM media_files;")
            return

        with get_db_connection() as conn:
            cursor = conn.cursor()
            # SQLite parameter limit handling
            placeholders = ",".join("?" for _ in active_relative_paths)
            cursor.execute(f"DELETE FROM media_files WHERE relative_path NOT IN ({placeholders})", active_relative_paths)


class SettingsModel:
    """Key-value settings storage."""

    @staticmethod
    def get(key: str, default: Optional[str] = None) -> Optional[str]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM settings WHERE key = ?", (key,))
            row = cursor.fetchone()
            return row["value"] if row else default

    @staticmethod
    def set(key: str, value: str):
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO settings (key, value, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(key) DO UPDATE SET
                    value=excluded.value,
                    updated_at=CURRENT_TIMESTAMP;
            """, (key, value))
