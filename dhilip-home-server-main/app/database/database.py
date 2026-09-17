"""
DhilipHome Server - SQLite Database Connection Manager
Provides thread-safe SQLite connection context and schema migrations.
"""

import sqlite3
import logging
from contextlib import contextmanager
from typing import Generator
from app.utils.config import Config

logger = logging.getLogger("dhiliphome.database")


def get_db_path() -> str:
    """Return database file path and ensure directory exists."""
    Config.DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)
    return str(Config.DATABASE_PATH)


@contextmanager
def get_db_connection() -> Generator[sqlite3.Connection, None, None]:
    """Context manager for SQLite connections with dict-like row access."""
    db_path = get_db_path()
    conn = sqlite3.connect(db_path, timeout=15.0)
    conn.row_factory = sqlite3.Row
    # Enable WAL mode for concurrent reads & writes
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    try:
        yield conn
        conn.commit()
    except Exception as e:
        conn.rollback()
        logger.error(f"Database transaction rolled back: {e}")
        raise
    finally:
        conn.close()


def init_db():
    """Initialize database tables and seed default administrator if absent."""
    with get_db_connection() as conn:
        cursor = conn.cursor()

        # 1. Server Info Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS server_info (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)

        # 2. Media Files Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS media_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                relative_path TEXT NOT NULL UNIQUE,
                category TEXT NOT NULL,
                extension TEXT,
                mime_type TEXT,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                modified_time REAL NOT NULL DEFAULT 0,
                indexed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_media_category ON media_files(category);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_media_filename ON media_files(filename);")

        # 3. Users Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'user',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login TIMESTAMP
            );
        """)

        # 4. Settings Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)

        # 5. Persistent server-side cloud download jobs.
        # This is intentionally server-authoritative; Android never stores the job as truth.
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS download_jobs (
                task_id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                filename TEXT NOT NULL,
                destination TEXT NOT NULL DEFAULT "",
                status TEXT NOT NULL DEFAULT "queued",
                progress_percent INTEGER NOT NULL DEFAULT 0,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                speed_bps INTEGER NOT NULL DEFAULT 0,
                average_speed_bps INTEGER NOT NULL DEFAULT 0,
                eta_seconds INTEGER,
                error TEXT,
                path TEXT,
                created_at REAL NOT NULL,
                updated_at REAL NOT NULL,
                started_at REAL,
                completed_at REAL
            );
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_download_jobs_status ON download_jobs(status);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_download_jobs_updated ON download_jobs(updated_at);")
        # A server restart cannot leave an in-memory worker running. Preserve
        # all progress and mark interrupted jobs resumable rather than losing state.
        cursor.execute("""
            UPDATE download_jobs
            SET status='paused', speed_bps=0, updated_at=?
            WHERE status IN ('queued','downloading','cancelling','pausing')
        """, (__import__("time").time(),))

        # Store initial server metadata
        cursor.execute("""
            INSERT OR REPLACE INTO server_info (key, value, updated_at)
            VALUES ('version', ?, CURRENT_TIMESTAMP),
                   ('initialized_at', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                   ('server_name', ?, CURRENT_TIMESTAMP);
        """, (Config.VERSION, Config.NAME))

        # Check and seed initial admin user
        cursor.execute("SELECT id FROM users WHERE username = ?", (Config.ADMIN_USERNAME,))
        existing_admin = cursor.fetchone()
        if not existing_admin:
            from app.utils.security import hash_password
            hashed = hash_password(Config.ADMIN_PASSWORD)
            cursor.execute("""
                INSERT INTO users (username, password_hash, role)
                VALUES (?, ?, 'admin');
            """, (Config.ADMIN_USERNAME, hashed))
            logger.info(f"Initialized default admin user: {Config.ADMIN_USERNAME}")

    logger.info("Database schema initialized successfully.")
