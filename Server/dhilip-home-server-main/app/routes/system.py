"""
DhilipHome Server - System Monitor Routes
Exposes hardware statistics, CPU, RAM, Swap, Uptime, and OS details.
"""

from flask import Blueprint, jsonify
from app.services.system_service import SystemService
from app.utils.security import success_response, error_response

system_bp = Blueprint("system", __name__)


@system_bp.route("/api/system", methods=["GET"])
def get_system():
    """
    Complete system monitor endpoint (Section 7).
    Collects CPU, RAM, Swap, Uptime, Operating System, and Process counts.
    """
    try:
        metrics = SystemService.get_complete_system_metrics()
        return jsonify(metrics), 200
    except Exception as e:
        return error_response("SYSTEM_METRIC_ERROR", f"Failed to gather system metrics: {str(e)}", 500)


@system_bp.route("/api/system/cpu", methods=["GET"])
def get_cpu():
    """Detailed CPU metrics."""
    try:
        return jsonify(SystemService.get_cpu_info()), 200
    except Exception as e:
        return error_response("CPU_METRIC_ERROR", str(e), 500)


@system_bp.route("/api/system/memory", methods=["GET"])
def get_memory():
    """Detailed RAM & Swap memory metrics."""
    try:
        return jsonify({
            "memory": SystemService.get_memory_info(),
            "swap": SystemService.get_swap_info(),
        }), 200
    except Exception as e:
        return error_response("MEMORY_METRIC_ERROR", str(e), 500)
