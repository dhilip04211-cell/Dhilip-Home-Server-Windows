"""
DhilipHome Server - Media Streaming & Catalog Routes
Exposes categorized media catalog, SQLite full-text search, on-demand indexing,
and HTTP 206 partial content streaming optimized for Android VideoView, ExoPlayer, and VLC.
"""

from flask import Blueprint, request, Response, jsonify
from app.services.media_service import MediaService
from app.utils.security import require_auth, success_response, error_response, get_request_token, verify_token
from app.utils.config import Config

media_bp = Blueprint("media", __name__)


@media_bp.route("/api/media", methods=["GET"])
def get_media_catalog():
    """
    Get categorized media catalog (Section 11).
    Optional query parameter: category (Videos, Movies, TV, Music, Images, Documents, Other).
    """
    category = request.args.get("category")
    try:
        data = MediaService.list_media(category=category)
        return success_response(data)
    except Exception as e:
        return error_response("MEDIA_CATALOG_ERROR", str(e), 500)


@media_bp.route("/api/media/search", methods=["GET"])
def search_media():
    """
    Search indexed media files by filename (Section 12).
    Query parameters:
    - q: search keyword
    - category: optional filter
    """
    query = request.args.get("q", "").strip()
    category = request.args.get("category")
    try:
        results = MediaService.search_media(query=query, category=category)
        return success_response({"query": query, "total_results": len(results), "items": results})
    except Exception as e:
        return error_response("MEDIA_SEARCH_ERROR", str(e), 500)


@media_bp.route("/api/media/scan", methods=["POST"])
@require_auth
def scan_media():
    """
    Trigger media directory scan and update SQLite database (Section 12).
    Broadcasts 'media_scan_progress' over WebSocket if clients are connected.
    """
    from app import socketio

    def progress_callback(scanned, current_file):
        try:
            if socketio:
                socketio.emit("media_scan_progress", {
                    "scanned": scanned,
                    "current_file": current_file,
                })
        except Exception:
            pass

    try:
        summary = MediaService.scan_and_index(progress_callback=progress_callback)
        if socketio:
            socketio.emit("media_scan_progress", {
                "scanned": summary["total_scanned"],
                "status": "COMPLETED",
                "categories": summary["categories"],
            })
        return success_response(summary, "Media directory scanned and indexed successfully")
    except Exception as e:
        return error_response("SCAN_FAILED", f"Media scan error: {str(e)}", 500)


@media_bp.route("/api/media/stream/<path:subpath>", methods=["GET", "HEAD"])
def stream_media(subpath: str):
    """
    Stream video/audio file with HTTP Range support (Section 11).
    Allows smooth seeking on Android ExoPlayer, Android TV, and Jio devices
    without reading whole file into RAM.
    Supports query parameter 'token' for authenticated media players.
    """
    token = get_request_token()
    valid, _, err = verify_token(token)
    if not valid:
        return error_response("AUTH_REQUIRED", f"Authentication failed: {err}", 401)

    try:
        range_header = request.headers.get("Range")
        generator, start, end, total_size, mime_type = MediaService.prepare_range_stream(
            relative_path=subpath,
            range_header=range_header,
        )

        content_length = (end - start) + 1

        headers = {
            "Content-Type": mime_type,
            "Accept-Ranges": "bytes",
            "Content-Range": f"bytes {start}-{end}/{total_size}",
            "Content-Length": str(content_length),
            "Cache-Control": "private, max-age=3600",
        }

        # For HEAD request, return headers only
        if request.method == "HEAD":
            return Response(status=206 if range_header else 200, headers=headers)

        status_code = 206 if range_header else 200
        return Response(generator, status=status_code, headers=headers, direct_passthrough=True)

    except FileNotFoundError as e:
        return error_response("MEDIA_NOT_FOUND", str(e), 404)
    except Exception as e:
        return error_response("STREAM_ERROR", f"Streaming failed: {str(e)}", 500)
