"""DhilipHome Server - live dashboard data endpoints."""
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from flask import Blueprint

from app.database.models import UserModel
from app.utils.config import Config, format_bytes
from app.utils.security import require_auth, success_response, error_response


dashboard_bp = Blueprint("dashboard", __name__)


def _service_state(active_state: str, sub_state: str) -> str:
    if active_state == "active":
        return "running"
    if active_state in {"inactive", "failed", "deactivating"}:
        return "stopped"
    return "unknown"


def _systemd_services():
    try:
        result = subprocess.run(
            ["systemctl", "list-units", "--type=service", "--all", "--no-legend", "--no-pager"],
            capture_output=True, text=True, timeout=5, check=False,
        )
        items = []
        for line in result.stdout.splitlines():
            parts = line.split(None, 4)
            if len(parts) < 4 or not parts[0].endswith(".service"):
                continue
            unit, load_state, active_state, sub_state = parts[:4]
            description = parts[4].strip() if len(parts) > 4 else ""
            memory_mb = 0
            uptime = "Unavailable"
            try:
                show = subprocess.run(
                    ["systemctl", "show", unit, "--property=MemoryCurrent,ActiveEnterTimestamp"],
                    capture_output=True, text=True, timeout=2, check=False,
                )
                values = {}
                for x in show.stdout.splitlines():
                    if "=" in x:
                        k, v = x.split("=", 1)
                        values[k] = v
                mem = int(values.get("MemoryCurrent") or 0)
                memory_mb = round(mem / (1024 * 1024)) if mem > 0 else 0
                started = values.get("ActiveEnterTimestamp", "")
                if started and active_state == "active":
                    dt = datetime.strptime(started[:24], "%a %Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
                    seconds = max(0, int(datetime.now(timezone.utc).timestamp() - dt.timestamp()))
                    days, rem = divmod(seconds, 86400)
                    hours, rem = divmod(rem, 3600)
                    minutes = rem // 60
                    uptime = f"{days}d {hours}h {minutes}m" if days else f"{hours}h {minutes}m"
            except Exception:
                pass
            items.append({
                "name": unit.removesuffix(".service"),
                "unit": unit,
                "state": _service_state(active_state, sub_state),
                "port": None,
                "description": description,
                "uptime": uptime,
                "memory_usage_mb": memory_mb,
            })
        return items
    except Exception:
        return []


@dashboard_bp.get("/api/services")
@require_auth
def services():
    return success_response({"items": _systemd_services()})


@dashboard_bp.get("/api/users")
@require_auth
def users():
    try:
        items = []
        for user in UserModel.list_all():
            last_login = user.get("last_login")
            items.append({
                "id": str(user["id"]),
                "username": user["username"],
                "display_name": user["username"],
                "role": user["role"],
                "last_active": last_login or "Never",
                "status": "Active",
            })
        return success_response({"items": items})
    except Exception as exc:
        return error_response("USERS_QUERY_ERROR", str(exc), 500)


@dashboard_bp.get("/api/activity")
@require_auth
def activity():
    """Return real recent server file changes; never invent activity records."""
    items = []
    try:
        candidates = []
        for root, dirs, files in os.walk(Config.MEDIA_ROOT):
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            for filename in files:
                path = Path(root) / filename
                try:
                    stat = path.stat()
                    rel = path.relative_to(Config.MEDIA_ROOT).as_posix()
                    candidates.append((stat.st_mtime, path, rel, stat.st_size))
                except (OSError, ValueError):
                    continue
        candidates.sort(key=lambda x: x[0], reverse=True)
        for idx, (mtime, path, rel, size) in enumerate(candidates[:20]):
            ext = path.suffix.lower()
            typ = "video" if ext in {".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv", ".ts", ".m4v", ".3gp", ".vob"} else \
                  "audio" if ext in {".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".wma", ".opus", ".alac", ".aiff"} else \
                  "image" if ext in {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".tiff", ".ico"} else \
                  "document" if ext in {".pdf", ".txt", ".doc", ".docx", ".epub", ".mobi", ".md", ".rtf", ".odt", ".csv"} else "other"
            items.append({
                "id": f"file-{int(mtime)}-{idx}",
                "title": path.name,
                "type": typ,
                "type_name": "Recent Server File",
                "timestamp": datetime.fromtimestamp(mtime, tz=timezone.utc).isoformat(),
                "size_text": format_bytes(size),
                "path": rel,
            })
    except Exception as exc:
        return error_response("ACTIVITY_QUERY_ERROR", str(exc), 500)
    return success_response({"items": items})
