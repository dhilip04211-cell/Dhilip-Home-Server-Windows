"""
DhilipHome Server - Storage Service
Detects mounted drives and partitions, calculates disk usage, and filters pseudo filesystems.
"""

import psutil
from app.utils.config import format_bytes, Config

# Virtual/Pseudo filesystems to exclude from consumer storage list
EXCLUDED_FSTYPES = {
    "sysfs", "proc", "devtmpfs", "devpts", "securityfs", "cgroup", "cgroup2",
    "pstore", "bpf", "autofs", "mqueue", "hugetlbfs", "debugfs", "tracefs",
    "fusectl", "configfs", "ramfs", "squashfs", "overlay"
}


class StorageService:
    """Service for querying mounted disk storage, capacities, and space utilization."""

    @classmethod
    def get_storage_info(cls) -> dict:
        """
        Scan all active mounted filesystems and return capacity and free space stats.
        Conforms to Section 8 of DhilipHome Server specification.
        """
        drives = []
        seen_mounts = set()

        try:
            partitions = psutil.disk_partitions(all=False)
        except Exception:
            partitions = []

        for p in partitions:
            # Skip pseudo/virtual mounts and loop devices
            if p.fstype.lower() in EXCLUDED_FSTYPES:
                continue
            if p.mountpoint in seen_mounts:
                continue
            if p.device.startswith("/dev/loop"):
                continue

            try:
                usage = psutil.disk_usage(p.mountpoint)
                drives.append({
                    "mount": p.mountpoint,
                    "device": p.device,
                    "filesystem": p.fstype,
                    "total_bytes": usage.total,
                    "used_bytes": usage.used,
                    "free_bytes": usage.free,
                    "usage_percent": round(usage.percent, 1),
                    "total_human": format_bytes(usage.total),
                    "used_human": format_bytes(usage.used),
                    "free_human": format_bytes(usage.free),
                })
                seen_mounts.add(p.mountpoint)
            except (PermissionError, OSError):
                continue

        # If no partition was found (e.g., container sandbox), ensure root mount is present
        if not drives:
            try:
                usage = psutil.disk_usage("/")
                drives.append({
                    "mount": "/",
                    "device": "/dev/root",
                    "filesystem": "ext4",
                    "total_bytes": usage.total,
                    "used_bytes": usage.used,
                    "free_bytes": usage.free,
                    "usage_percent": round(usage.percent, 1),
                    "total_human": format_bytes(usage.total),
                    "used_human": format_bytes(usage.used),
                    "free_human": format_bytes(usage.free),
                })
            except Exception:
                pass

        # Also provide media directory specific usage
        media_storage = None
        try:
            m_usage = psutil.disk_usage(str(Config.MEDIA_ROOT))
            media_storage = {
                "path": str(Config.MEDIA_ROOT),
                "total_bytes": m_usage.total,
                "used_bytes": m_usage.used,
                "free_bytes": m_usage.free,
                "usage_percent": round(m_usage.percent, 1),
                "total_human": format_bytes(m_usage.total),
                "used_human": format_bytes(m_usage.used),
                "free_human": format_bytes(m_usage.free),
            }
        except Exception:
            pass

        return {
            "drives": drives,
            "media_storage": media_storage,
        }
