"""
DhilipHome Server - Network Routes
Exposes network interfaces, active IP addresses, adapter statuses, and I/O counters.
"""

from flask import Blueprint, jsonify
from app.services.network_service import NetworkService
from app.utils.security import error_response

network_bp = Blueprint("network", __name__)


@network_bp.route("/api/network", methods=["GET"])
def get_network():
    """
    Network information endpoint (Section 9).
    Exposes active interfaces, LAN IPs, and transfer statistics.
    """
    try:
        data = NetworkService.get_network_info()
        return jsonify(data), 200
    except Exception as e:
        return error_response("NETWORK_QUERY_ERROR", f"Failed to gather network metrics: {str(e)}", 500)
