"""Secure file management and server-side cloud downloads.

Cloud downloads are executed directly by the Home Server.  The Android client
only submits commands and observes server state; no Cloudflare/proxy or
Android-side transfer is involved.
"""
import os
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
import urllib.error
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from flask import Blueprint, request, send_file
from werkzeug.utils import secure_filename

from app.services.file_service import FileService
from app.utils.config import Config
from app.utils.security import require_auth, success_response, error_response

files_bp = Blueprint("files", __name__)
_remote_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="dhilip-remote-download")
_remote_lock = threading.RLock()
_remote_control = {}  # task_id -> {"pause": bool, "cancel": bool}
# Direct outbound HTTP from the Home Server. No Cloudflare, reverse proxy, or
# environment proxy is used for cloud-download workers.
_direct_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _db():
    conn = sqlite3.connect(str(Config.DATABASE_PATH), timeout=15.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def _row(task_id):
    with _db() as conn:
        r = conn.execute("SELECT * FROM download_jobs WHERE task_id=?", (task_id,)).fetchone()
        return dict(r) if r else None


def _save(task_id, **changes):
    if not changes:
        return _row(task_id)
    changes["updated_at"] = time.time()
    cols = ", ".join(f"{k}=?" for k in changes)
    vals = list(changes.values()) + [task_id]
    with _db() as conn:
        conn.execute(f"UPDATE download_jobs SET {cols} WHERE task_id=?", vals)
    return _row(task_id)


def _is_admin():
    return str(getattr(request, "current_user", {}).get("role", "")).lower() == "admin"


def require_admin():
    if not _is_admin():
        return error_response("ADMIN_REQUIRED", "Administrator permission is required for this action", 403)
    return None


def _safe_destination(destination):
    # FileService already strips a leading slash; this additionally normalizes
    # separators so "/Movies" and "Movies" refer to the same server folder.
    return destination.strip().replace("\\", "/").lstrip("/")


def _remote_download_worker(task_id):
    task = _row(task_id)
    if not task:
        return
    destination = _safe_destination(task["destination"])
    filename = secure_filename(task["filename"]) or "download.bin"
    target_dir = FileService.resolve_safe_path(destination)
    if target_dir is None or not target_dir.is_dir():
        _save(task_id, status="failed", error="Destination folder does not exist inside MEDIA_ROOT", speed_bps=0)
        return

    target = target_dir / filename
    if target.exists():
        _save(task_id, status="failed", error=f"File '{target.name}' already exists", speed_bps=0)
        return

    tmp = target.with_name(target.name + ".part")
    downloaded = tmp.stat().st_size if tmp.exists() else 0
    started = time.monotonic()
    last_bytes = downloaded
    last_time = started
    total = int(task.get("total_bytes") or 0)

    try:
        while True:
            with _remote_lock:
                control = _remote_control.setdefault(task_id, {"pause": False, "cancel": False})
                if control["cancel"]:
                    _save(task_id, status="cancelled", error="Cancelled by user", speed_bps=0)
                    try:
                        tmp.unlink(missing_ok=True)
                    except Exception:
                        pass
                    return
                if control["pause"]:
                    _save(task_id, status="paused", speed_bps=0, error=None)
                    return

            headers = {
                "User-Agent": "DhilipHome-Server/0.3.1",
                "Accept": "*/*",
                "Accept-Encoding": "identity",
            }
            if downloaded > 0:
                headers["Range"] = f"bytes={downloaded}-"

            req = urllib.request.Request(task["url"], headers=headers, method="GET")
            try:
                response = _direct_opener.open(req, timeout=15)
            except urllib.error.HTTPError as exc:
                # Client errors are permanent and should be shown to the user.
                # Server-side 5xx errors are transient and can be retried.
                if 400 <= exc.code < 500 and exc.code != 408 and exc.code != 429:
                    _save(task_id, status="failed", error=f"Remote server returned HTTP {exc.code}", speed_bps=0)
                    return
                time.sleep(2)
                continue
            except (urllib.error.URLError, TimeoutError, ConnectionError, OSError):
                # Temporary network failure: preserve .part and retry from the
                # current byte offset. The Android client is not involved.
                time.sleep(2)
                continue

            with response:
                response_total = int(response.headers.get("Content-Length") or 0)
                content_range = response.headers.get("Content-Range", "")
                if content_range and "/" in content_range:
                    try:
                        total = int(content_range.rsplit("/", 1)[1])
                    except ValueError:
                        pass
                elif response_total and response.status == 200:
                    # Server ignored Range; safely restart from zero.
                    if downloaded:
                        downloaded = 0
                        tmp.unlink(missing_ok=True)
                    total = response_total
                elif response_total and total == 0:
                    total = downloaded + response_total

                mode = "ab" if downloaded > 0 and response.status == 206 else "wb"
                if mode == "wb":
                    downloaded = 0
                    last_bytes = 0
                _save(task_id, status="downloading", total_bytes=total, downloaded_bytes=downloaded,
                      progress_percent=int(downloaded * 100 / total) if total else 0, speed_bps=0,
                      started_at=task.get("started_at") or time.time(), error=None)

                with open(tmp, mode) as out:
                    while True:
                        with _remote_lock:
                            control = _remote_control.setdefault(task_id, {"pause": False, "cancel": False})
                            if control["cancel"]:
                                _save(task_id, status="cancelled", downloaded_bytes=downloaded, speed_bps=0,
                                      error="Cancelled by user")
                                out.flush()
                                try:
                                    tmp.unlink(missing_ok=True)
                                except Exception:
                                    pass
                                return
                            if control["pause"]:
                                out.flush()
                                os.fsync(out.fileno())
                                _save(task_id, status="paused", downloaded_bytes=downloaded, speed_bps=0, error=None)
                                return

                        chunk = response.read(1024 * 1024)
                        if not chunk:
                            break
                        out.write(chunk)
                        downloaded += len(chunk)

                        now = time.monotonic()
                        if now - last_time >= 0.5:
                            dt = now - last_time
                            speed = max(0, int((downloaded - last_bytes) / dt))
                            percent = int(downloaded * 100 / total) if total else 0
                            eta = int((total - downloaded) / speed) if total > downloaded and speed > 0 else None
                            _save(task_id, downloaded_bytes=downloaded,
                                  progress_percent=min(99, percent), speed_bps=speed,
                                  average_speed_bps=int(downloaded / max(now - started, 0.001)),
                                  eta_seconds=eta)
                            last_bytes, last_time = downloaded, now
                    out.flush()
                    os.fsync(out.fileno())

            # A complete response exits the retry loop. Unknown-length streams
            # are considered complete when the HTTP response ends normally.
            if total and downloaded < total:
                time.sleep(1)
                continue
            break

        os.replace(tmp, target)
        size = target.stat().st_size
        try:
            from app.services.media_service import MediaService
            MediaService.scan_and_index()
        except Exception:
            pass
        rel = target.relative_to(Config.MEDIA_ROOT).as_posix()
        _save(task_id, status="completed", progress_percent=100, downloaded_bytes=size,
              total_bytes=total or size, speed_bps=0, average_speed_bps=0, eta_seconds=0,
              path=rel, completed_at=time.time(), error=None)
    except Exception as exc:
        # Preserve the partial file for a resumable retry unless cancellation
        # explicitly removed it.
        _save(task_id, status="failed", downloaded_bytes=downloaded, total_bytes=total,
              speed_bps=0, eta_seconds=None, error=str(exc))
    finally:
        with _remote_lock:
            _remote_control.pop(task_id, None)


def _start_worker(task_id):
    with _remote_lock:
        _remote_control[task_id] = {"pause": False, "cancel": False}
    _save(task_id, status="queued", error=None)
    _remote_executor.submit(_remote_download_worker, task_id)


@files_bp.route("/api/files/remote-download", methods=["POST"])
@require_auth
def start_remote_download():
    data = request.get_json(silent=True) or {}
    url = str(data.get("url", "")).strip()
    destination = _safe_destination(str(data.get("destination", "") or ""))
    filename = str(data.get("filename", "") or "").strip()

    if not url or not url.lower().startswith(("http://", "https://")):
        return error_response("INVALID_URL", "Only HTTP/HTTPS URLs are supported", 400)
    if not filename:
        filename = Path(urllib.parse.urlparse(url).path).name or f"download_{int(time.time())}.bin"
    filename = secure_filename(filename)
    if not filename:
        return error_response("INVALID_FILENAME", "Invalid destination filename", 400)

    target_dir = FileService.resolve_safe_path(destination)
    if target_dir is None or not target_dir.is_dir():
        return error_response("DESTINATION_NOT_FOUND", "Destination folder does not exist", 404)

    task_id = "srvdl_" + uuid.uuid4().hex[:12]
    now = time.time()
    with _db() as conn:
        conn.execute("""
            INSERT INTO download_jobs
            (task_id,url,filename,destination,status,progress_percent,downloaded_bytes,
             total_bytes,speed_bps,average_speed_bps,eta_seconds,error,path,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, (task_id, url, filename, destination, "queued", 0, 0, 0, 0, 0, None,
              None, None, now, now))

    # Return immediately; the actual Internet transfer is performed by the server.
    _start_worker(task_id)
    return success_response(_row(task_id), "Server download started", 202)


@files_bp.route("/api/files/remote-download", methods=["GET"])
@require_auth
def remote_download_status():
    task_id = request.args.get("task_id", "").strip()
    if task_id:
        task = _row(task_id)
        if not task:
            return error_response("TASK_NOT_FOUND", "Download task was not found", 404)
        return success_response(task)

    with _db() as conn:
        rows = conn.execute(
            "SELECT * FROM download_jobs ORDER BY updated_at DESC LIMIT 100"
        ).fetchall()
    return success_response([dict(r) for r in rows])


def _control_download(action):
    data = request.get_json(silent=True) or {}
    task_id = str(data.get("task_id", "")).strip()
    if not task_id:
        return error_response("TASK_ID_REQUIRED", "task_id is required", 400)
    task = _row(task_id)
    if not task:
        return error_response("TASK_NOT_FOUND", "Download task was not found", 404)
    if task["status"] == "completed":
        return success_response(task, "Download already completed")
    with _remote_lock:
        ctl = _remote_control.setdefault(task_id, {"pause": False, "cancel": False})
        if action == "pause":
            ctl["pause"] = True
            _save(task_id, status="pausing")
        elif action == "cancel":
            ctl["cancel"] = True
            _save(task_id, status="cancelling")
        elif action == "resume":
            ctl["pause"] = False
            ctl["cancel"] = False
            _start_worker(task_id)
    return success_response(_row(task_id), f"Download {action} requested")


@files_bp.route("/api/files/remote-download/pause", methods=["POST"])
@require_auth
def pause_remote_download():
    return _control_download("pause")


@files_bp.route("/api/files/remote-download/resume", methods=["POST"])
@require_auth
def resume_remote_download():
    return _control_download("resume")


@files_bp.route("/api/files/remote-download/cancel", methods=["POST"])
@require_auth
def cancel_remote_download():
    return _control_download("cancel")


@files_bp.route("/api/files", methods=["GET"])
@files_bp.route("/api/files/list", methods=["GET"])
@require_auth
def list_files():
    path_arg = request.args.get("path", "")
    try:
        return success_response(FileService.list_directory(path_arg))
    except FileNotFoundError as e:
        return error_response("NOT_FOUND", str(e), 404)
    except NotADirectoryError as e:
        return error_response("NOT_A_DIRECTORY", str(e), 400)
    except Exception as e:
        return error_response("LIST_FILES_ERROR", str(e), 500)


@files_bp.route("/api/files/info", methods=["GET"])
@require_auth
def get_file_info():
    path_arg = request.args.get("path", "")
    if not path_arg:
        return error_response("PARAM_REQUIRED", "Query parameter 'path' is required", 400)
    try:
        return success_response(FileService.get_item_info(path_arg))
    except FileNotFoundError as e:
        return error_response("FILE_NOT_FOUND", str(e), 404)
    except Exception as e:
        return error_response("FILE_INFO_ERROR", str(e), 500)


@files_bp.route("/api/files/download", methods=["GET", "HEAD"])
def download_file():
    from app.utils.security import get_request_token, verify_token
    valid, _, err = verify_token(get_request_token())
    if not valid:
        return error_response("AUTH_REQUIRED", f"Authentication failed: {err}", 401)
    path_arg = request.args.get("path", "")
    if not path_arg:
        return error_response("PARAM_REQUIRED", "Query parameter 'path' is required", 400)
    target = FileService.resolve_safe_path(path_arg)
    if target is None or not target.is_file():
        return error_response("FILE_NOT_FOUND", "File was not found or invalid path", 404)
    return send_file(str(target), as_attachment=True, download_name=target.name, conditional=True, etag=True)


@files_bp.route("/api/files/upload", methods=["POST"])
@require_auth
def upload_file():
    if "file" not in request.files:
        return error_response("NO_FILE_UPLOADED", "No file found in multipart upload", 400)
    uploaded = request.files["file"]
    parent_path = request.form.get("path", "")
    try:
        info = FileService.save_uploaded_file(parent_path, uploaded)
        try:
            from app.services.media_service import MediaService
            MediaService.scan_and_index()
        except Exception:
            pass
        return success_response(info, "File uploaded successfully", 201)
    except FileNotFoundError as e:
        return error_response("TARGET_DIR_NOT_FOUND", str(e), 404)
    except ValueError as e:
        return error_response("INVALID_UPLOAD", str(e), 400)
    except Exception as e:
        return error_response("UPLOAD_FAILED", str(e), 500)


@files_bp.route("/api/files/folder", methods=["POST"])
@require_auth
def create_folder():
    data = request.get_json(silent=True) or {}
    folder_name = data.get("name") or request.form.get("name", "")
    parent_path = data.get("path") or request.form.get("path", "")
    if not folder_name:
        return error_response("NAME_REQUIRED", "Folder name is required", 400)
    try:
        return success_response(FileService.create_folder(parent_path, folder_name), "Folder created successfully", 201)
    except FileExistsError as e:
        return error_response("FOLDER_EXISTS", str(e), 409)
    except (FileNotFoundError, ValueError) as e:
        return error_response("FOLDER_CREATE_ERROR", str(e), 400)
    except Exception as e:
        return error_response("FOLDER_CREATE_FAILED", str(e), 500)


@files_bp.route("/api/files/rename", methods=["POST"])
@require_auth
def rename_file_or_folder():
    denied = require_admin()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    path_arg = str(data.get("path", "")).strip()
    new_name = str(data.get("new_name", "")).strip()
    if not path_arg or not new_name:
        return error_response("PARAM_REQUIRED", "path and new_name are required", 400)
    try:
        target = FileService.resolve_safe_path(path_arg)
        if target is None or not target.exists() or target == Config.MEDIA_ROOT:
            return error_response("FILE_NOT_FOUND", "Target item not found", 404)
        safe_name = secure_filename(new_name)
        if not safe_name:
            return error_response("INVALID_NAME", "Invalid new name", 400)
        destination = target.parent / safe_name
        if not FileService.resolve_safe_path(destination.relative_to(Config.MEDIA_ROOT).as_posix()):
            return error_response("INVALID_PATH", "Invalid destination path", 400)
        if destination.exists():
            return error_response("NAME_EXISTS", "An item with that name already exists", 409)
        target.rename(destination)
        try:
            from app.services.media_service import MediaService
            MediaService.scan_and_index()
        except Exception:
            pass
        return success_response(FileService.get_item_info(destination.relative_to(Config.MEDIA_ROOT).as_posix()), "Item renamed successfully")
    except Exception as e:
        return error_response("RENAME_FAILED", str(e), 500)


@files_bp.route("/api/files", methods=["DELETE"])
@require_auth
def delete_file_or_folder():
    denied = require_admin()
    if denied:
        return denied
    path_arg = request.args.get("path")
    if not path_arg:
        data = request.get_json(silent=True) or {}
        path_arg = data.get("path", "")
    if not path_arg:
        return error_response("PATH_REQUIRED", "Parameter 'path' is required", 400)
    try:
        FileService.delete_item(path_arg)
        try:
            from app.services.media_service import MediaService
            MediaService.scan_and_index()
        except Exception:
            pass
        return success_response({"deleted_path": path_arg}, "Item deleted successfully")
    except FileNotFoundError as e:
        return error_response("FILE_NOT_FOUND", str(e), 404)
    except PermissionError as e:
        return error_response("PERMISSION_DENIED", str(e), 403)
    except Exception as e:
        return error_response("DELETE_FAILED", str(e), 500)
