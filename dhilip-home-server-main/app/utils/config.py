"""
DhilipHome Server - Configuration Utility
Handles environment configuration, directory resolution, and network IP detection.
"""

import os
import socket
import sys
from pathlib import Path
from dotenv import load_dotenv

# Load .env file from project root
# When packaged with PyInstaller, keep persistent data beside the EXE rather than
# inside the temporary one-file extraction directory. This preserves the same
# server state across restarts and Android cache clears.
if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys.executable).resolve().parent
else:
    BASE_DIR = Path(__file__).resolve().parent.parent.parent
load_dotenv(dotenv_path=BASE_DIR / ".env")


def get_lan_ip() -> str:
    """
    Automatically detect the primary local network (LAN) IP address.
    Does not make an outbound network request; queries the OS routing table.
    """
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        # Connect to public DNS to inspect outbound routing interface
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


def format_bytes(size_bytes: int) -> str:
    """Format bytes into human-readable representation."""
    if size_bytes is None or size_bytes < 0:
        return "0 B"
    units = ["B", "KB", "MB", "GB", "TB", "PB"]
    val = float(size_bytes)
    for unit in units:
        if val < 1024.0:
            return f"{val:.2f} {unit}"
        val /= 1024.0
    return f"{val:.2f} PB"


def format_duration(seconds: float) -> str:
    """Format seconds into human-readable duration (days, hours, minutes, seconds)."""
    if seconds is None or seconds < 0:
        return "0s"
    s = int(seconds)
    days, rem = divmod(s, 86400)
    hours, rem = divmod(rem, 3600)
    minutes, secs = divmod(rem, 60)
    parts = []
    if days > 0:
        parts.append(f"{days}d")
    if hours > 0 or days > 0:
        parts.append(f"{hours}h")
    if minutes > 0 or hours > 0 or days > 0:
        parts.append(f"{minutes}m")
    parts.append(f"{secs}s")
    return " ".join(parts)


class Config:
    """Global configuration settings for DhilipHome Server."""

    # Server Info
    NAME = "DhilipHome Server"
    VERSION = "0.3.1"

    # Network Binding
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", "8080"))
    LAN_IP = get_lan_ip()
    HOSTNAME = socket.gethostname()

    # Paths
    BASE_DIR = BASE_DIR
    MEDIA_ROOT = Path(os.getenv("MEDIA_ROOT", str(BASE_DIR / "media"))).resolve()
    DATABASE_PATH = Path(os.getenv("DATABASE_PATH", str(BASE_DIR / "data" / "dhiliphome.db"))).resolve()
    LOG_PATH = Path(os.getenv("LOG_PATH", str(BASE_DIR / "logs" / "server.log"))).resolve()
    CONFIG_DIR = BASE_DIR / "config"

    # Security & Auth
    SECRET_KEY = os.getenv("DHILIPHOME_SECRET_KEY", "dhiliphome-insecure-dev-secret-key-replace-in-prod")
    ADMIN_USERNAME = os.getenv("DHILIPHOME_ADMIN_USERNAME", "admin")
    ADMIN_PASSWORD = os.getenv("DHILIPHOME_ADMIN_PASSWORD", "admin123")
    AUTH_TOKEN_EXPIRY = int(os.getenv("AUTH_TOKEN_EXPIRY_SECONDS", str(7 * 86400)))

    # Realtime & Discovery
    DISCOVERY_UDP_PORT = int(os.getenv("DISCOVERY_UDP_PORT", "8888"))
    SYSTEM_UPDATE_INTERVAL = int(os.getenv("SYSTEM_UPDATE_INTERVAL", "3"))
    CORS_ORIGINS = os.getenv("CORS_ORIGINS", "*")

    # Limits
    MAX_CONTENT_LENGTH = int(os.getenv("MAX_CONTENT_LENGTH_MB", "500")) * 1024 * 1024  # bytes

    @classmethod
    def ensure_directories(cls):
        """Ensure all required runtime directories exist."""
        cls.MEDIA_ROOT.mkdir(parents=True, exist_ok=True)
        cls.DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)
        cls.LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        cls.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
