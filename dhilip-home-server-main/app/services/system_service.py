"""
DhilipHome Server - System Monitor Service
Collects CPU, RAM, Swap, Uptime, OS, and Process metrics using psutil and standard libraries.
"""

import os
import time
import platform
import socket
from datetime import datetime, timezone
import psutil
from app.utils.config import format_bytes, format_duration


class SystemService:
    """Service for gathering real-time hardware, operating system, and system performance metrics."""

    _server_start_time = time.time()

    @classmethod
    def get_server_start_time(cls) -> float:
        return cls._server_start_time

    @classmethod
    def get_server_uptime_seconds(cls) -> int:
        return int(time.time() - cls._server_start_time)

    @classmethod
    def get_cpu_info(cls) -> dict:
        """Gather CPU metrics: utilization, core counts, frequency, and load averages."""
        try:
            usage = psutil.cpu_percent(interval=None)
            per_cpu = psutil.cpu_percent(interval=None, percpu=True)
        except Exception:
            usage = 0.0
            per_cpu = []

        # Frequency (may not be accessible in all containerized/virtualized environments)
        freq_info = None
        try:
            freq = psutil.cpu_freq()
            if freq:
                freq_info = {
                    "current_mhz": round(freq.current, 2) if freq.current else None,
                    "min_mhz": round(freq.min, 2) if freq.min else None,
                    "max_mhz": round(freq.max, 2) if freq.max else None,
                }
        except Exception:
            freq_info = None

        # Load averages
        load_avg = None
        try:
            if hasattr(os, "getloadavg"):
                l1, l5, l15 = os.getloadavg()
                load_avg = {
                    "1min": round(l1, 2),
                    "5min": round(l5, 2),
                    "15min": round(l15, 2),
                }
        except Exception:
            load_avg = None

        return {
            "usage_percent": round(usage, 1),
            "per_cpu_percent": [round(p, 1) for p in per_cpu],
            "physical_cores": psutil.cpu_count(logical=False) or 1,
            "logical_cores": psutil.cpu_count(logical=True) or 1,
            "frequency": freq_info,
            "load_average": load_avg,
        }

    @classmethod
    def get_memory_info(cls) -> dict:
        """Gather RAM memory metrics."""
        v = psutil.virtual_memory()
        return {
            "total_bytes": v.total,
            "used_bytes": v.used,
            "free_bytes": v.free,
            "available_bytes": v.available,
            "usage_percent": round(v.percent, 1),
            "total_human": format_bytes(v.total),
            "used_human": format_bytes(v.used),
            "free_human": format_bytes(v.free),
            "available_human": format_bytes(v.available),
        }

    @classmethod
    def get_swap_info(cls) -> dict:
        """Gather Swap memory metrics."""
        s = psutil.swap_memory()
        return {
            "total_bytes": s.total,
            "used_bytes": s.used,
            "free_bytes": s.free,
            "usage_percent": round(s.percent, 1),
            "total_human": format_bytes(s.total),
            "used_human": format_bytes(s.used),
            "free_human": format_bytes(s.free),
        }

    @classmethod
    def get_uptime_info(cls) -> dict:
        """Gather system uptime and boot time metrics."""
        boot_epoch = psutil.boot_time()
        system_uptime = time.time() - boot_epoch
        server_uptime = time.time() - cls._server_start_time

        boot_dt = datetime.fromtimestamp(boot_epoch, tz=timezone.utc).isoformat()

        return {
            "system_uptime_seconds": int(system_uptime),
            "system_uptime_human": format_duration(system_uptime),
            "server_uptime_seconds": int(server_uptime),
            "server_uptime_human": format_duration(server_uptime),
            "boot_time": boot_dt,
            "boot_timestamp": int(boot_epoch),
        }

    @classmethod
    def get_os_info(cls) -> dict:
        """Gather detailed OS and distribution info."""
        distro = "Linux"
        version = ""
        # Check /etc/os-release if on Linux/Debian
        try:
            if hasattr(platform, "freedesktop_os_release"):
                os_release = platform.freedesktop_os_release()
                distro = os_release.get("PRETTY_NAME", os_release.get("NAME", "Linux"))
                version = os_release.get("VERSION_ID", "")
            elif os.path.exists("/etc/os-release"):
                with open("/etc/os-release", "r") as f:
                    lines = f.readlines()
                for line in lines:
                    if line.startswith("PRETTY_NAME="):
                        distro = line.split("=", 1)[1].strip().strip('"')
                    elif line.startswith("VERSION_ID=") and not version:
                        version = line.split("=", 1)[1].strip().strip('"')
        except Exception:
            distro = platform.system()

        return {
            "os_name": platform.system(),
            "distribution": distro,
            "version": version or platform.version(),
            "kernel": platform.release(),
            "architecture": platform.machine(),
            "hostname": socket.gethostname(),
            "python_version": platform.python_version(),
        }

    @classmethod
    def get_process_info(cls) -> dict:
        """Gather process count statistics."""
        running = 0
        total = 0
        try:
            pids = psutil.pids()
            total = len(pids)
            for p in psutil.process_iter(['status']):
                try:
                    if p.info['status'] == psutil.STATUS_RUNNING:
                        running += 1
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass
        except Exception:
            total = len(psutil.pids()) if hasattr(psutil, "pids") else 0
            running = 1

        return {
            "total_count": total,
            "running_count": max(1, running),
        }

    @classmethod
    def get_complete_system_metrics(cls) -> dict:
        """Aggregate all system metrics conforming to Section 7."""
        return {
            "cpu": cls.get_cpu_info(),
            "memory": cls.get_memory_info(),
            "swap": cls.get_swap_info(),
            "uptime": cls.get_uptime_info(),
            "operating_system": cls.get_os_info(),
            "processes": cls.get_process_info(),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
