"""
DhilipHome Server - Storage Routes
Exposes mounted drives, partition statistics, and media storage capacity.
"""

from flask import Blueprint, jsonify
from app.services.storage_service import StorageService
from app.utils.security import error_response

storage_bp = Blueprint("storage", __name__)


@storage_bp.route("/api/storage", methods=["GET"])
def get_storage():
    """
    Storage capacity and disk utilization endpoint (Section 8).
    Detects active non-virtual filesystems.
    """
    try:
        storage_data = StorageService.get_storage_info()
        return jsonify(storage_data), 200
    except Exception as e:
        return error_response("STORAGE_QUERY_ERROR", f"Failed to detect disk storage: {str(e)}", 500)
