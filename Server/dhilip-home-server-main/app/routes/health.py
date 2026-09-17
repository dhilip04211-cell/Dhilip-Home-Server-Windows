"""
DhilipHome Server - Health & Discovery Routes
Public endpoints for server ping, runtime identification, and LAN discovery.
"""

from datetime import datetime, timezone
from flask import Blueprint, jsonify
from app.utils.config import Config, get_lan_ip
from app.services.system_service import SystemService

health_bp = Blueprint("health", __name__)


@health_bp.route("/api/health", methods=["GET"])
def health_check():
    """
    Health check endpoint (Section 5).
    Remains public for client polling and heartbeat monitoring.
    """
    uptime_sec = SystemService.get_server_uptime_seconds()
    return jsonify({
        "status": "ok",
        "server": Config.NAME,
        "version": Config.VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "uptime_seconds": uptime_sec,
    }), 200


@health_bp.route("/api/server", methods=["GET"])
def server_info():
    """
    Detailed server information endpoint (Section 6).
    Returns host identity, OS, Python version, IP, port, and uptime.
    """
    os_info = SystemService.get_os_info()
    uptime_sec = SystemService.get_server_uptime_seconds()
    start_time_iso = datetime.fromtimestamp(SystemService.get_server_start_time(), tz=timezone.utc).isoformat()
    now_iso = datetime.now(timezone.utc).isoformat()

    return jsonify({
        "name": Config.NAME,
        "version": Config.VERSION,
        "hostname": Config.HOSTNAME,
        "ip": get_lan_ip(),
        "port": Config.PORT,
        "os": f"{os_info['distribution']} {os_info['version']}".strip() or os_info["os_name"],
        "python_version": os_info["python_version"],
        "server_start_time": start_time_iso,
        "uptime_seconds": uptime_sec,
        "current_time": now_iso,
    }), 200


@health_bp.route("/api/discovery", methods=["GET"])
def discovery_info():
    """
    LAN server discovery endpoint (Section 18).
    Consumed by the DhilipHome Android app when broadcasting or probing local subnet.
    """
    return jsonify({
        "service": Config.NAME,
        "version": Config.VERSION,
        "port": Config.PORT,
        "api": "/api",
        "device_name": Config.HOSTNAME,
        "lan_ip": get_lan_ip(),
        "status": "ONLINE",
    }), 200
