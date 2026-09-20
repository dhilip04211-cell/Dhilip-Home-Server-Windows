"""
DhilipHome Server - Application Factory & Initialization
Initializes Flask, CORS, SQLite Database, WebSocket (Flask-SocketIO),
Rotating Logging, and Background Telemetry / UDP Discovery services.
"""

import os
import json
import time
import socket
import logging
from logging.handlers import RotatingFileHandler
import threading
from flask import Flask, jsonify
from flask_cors import CORS
from flask_socketio import SocketIO

from app.utils.config import Config, get_lan_ip
from app.database.database import init_db

# Global SocketIO instance
socketio = SocketIO()

# Background telemetry worker controller
_telemetry_thread_started = False
_telemetry_lock = threading.Lock()


def setup_logging(app: Flask):
    """Configure rotating file and console logging conforming to Section 19."""
    Config.LOG_PATH.parent.mkdir(parents=True, exist_ok=True)

    log_level = getattr(logging, os.getenv("LOG_LEVEL", "INFO").upper(), logging.INFO)

    file_handler = RotatingFileHandler(
        str(Config.LOG_PATH),
        maxBytes=10 * 1024 * 1024,  # 10 MB
        backupCount=5,
        encoding="utf-8",
    )
    formatter = logging.Formatter(
        "[%(asctime)s] [%(levelname)s] [%(name)s]: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    file_handler.setFormatter(formatter)
    file_handler.setLevel(log_level)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    console_handler.setLevel(log_level)

    app.logger.setLevel(log_level)
    app.logger.addHandler(file_handler)
    app.logger.addHandler(console_handler)

    # Suppress verbose websocket/engineio ping pong logs
    logging.getLogger("werkzeug").setLevel(logging.WARNING)
    logging.getLogger("engineio").setLevel(logging.WARNING)
    logging.getLogger("socketio").setLevel(logging.WARNING)


def start_udp_discovery_worker(app: Flask):
    """
    Background UDP discovery listener (Section 18).
    Listens for 'DHILIPHOME_DISCOVER' broadcast packets from Android devices
    and replies with server connection information.
    """
    def udp_listener():
        port = Config.DISCOVERY_UDP_PORT
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", port))
            app.logger.info(f"UDP LAN discovery listener active on port {port}")
            while True:
                data, addr = sock.recvfrom(1024)
                message = data.decode("utf-8", errors="ignore").strip()
                if "DHILIPHOME_DISCOVER" in message or "DHILIPHOME_PING" in message:
                    response_payload = {
                        "service": Config.NAME,
                        "version": Config.VERSION,
                        "port": Config.PORT,
                        "api": "/api",
                        "device_name": Config.HOSTNAME,
                        "lan_ip": get_lan_ip(),
                    }
                    reply_data = json.dumps(response_payload).encode("utf-8")
                    sock.sendto(reply_data, addr)
        except Exception as e:
            app.logger.debug(f"UDP discovery listener stopped or unavailable: {e}")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    thread = threading.Thread(target=udp_listener, daemon=True, name="udp-discovery")
    thread.start()


def start_system_telemetry_worker(app: Flask):
    """
    Background worker that broadcasts real-time system metrics (Section 17)
    over WebSocket event 'system_update' every Config.SYSTEM_UPDATE_INTERVAL seconds.
    """
    global _telemetry_thread_started
    with _telemetry_lock:
        if _telemetry_thread_started:
            return
        _telemetry_thread_started = True

    def telemetry_loop():
        from app.services.system_service import SystemService
        interval = max(2, min(Config.SYSTEM_UPDATE_INTERVAL, 10))
        while True:
            try:
                metrics = SystemService.get_complete_system_metrics()
                socketio.emit("system_update", metrics)
            except Exception:
                pass
            time.sleep(interval)

    thread = threading.Thread(target=telemetry_loop, daemon=True, name="system-telemetry")
    thread.start()


def create_app() -> Flask:
    """Application factory for DhilipHome Server."""
    # Ensure folders exist
    Config.ensure_directories()

    app = Flask(__name__)
    app.config["SECRET_KEY"] = Config.SECRET_KEY
    app.config["MAX_CONTENT_LENGTH"] = Config.MAX_CONTENT_LENGTH

    # Logging
    setup_logging(app)

    # Initialize SQLite Database
    try:
        init_db()
    except Exception as e:
        # A server without a writable/valid database cannot safely serve file
        # operations. Starting anyway only turns the real startup problem into
        # opaque HTTP 500 responses later.
        app.logger.exception("Failed to initialize SQLite database")
        raise RuntimeError(f"DhilipHome database initialization failed: {e}") from e

    # CORS configuration
    CORS(app, resources={r"/api/*": {"origins": Config.CORS_ORIGINS}}, supports_credentials=True)

    # WebSocket configuration
    socketio.init_app(app, cors_allowed_origins="*", async_mode="threading")

    # Register Route Blueprints
    from app.routes.health import health_bp
    from app.routes.system import system_bp
    from app.routes.storage import storage_bp
    from app.routes.network import network_bp
    from app.routes.files import files_bp
    from app.routes.media import media_bp
    from app.routes.auth import auth_bp
    from app.routes.dashboard import dashboard_bp

    app.register_blueprint(health_bp)
    app.register_blueprint(system_bp)
    app.register_blueprint(storage_bp)
    app.register_blueprint(network_bp)
    app.register_blueprint(files_bp)
    app.register_blueprint(media_bp)
    app.register_blueprint(auth_bp)
    app.register_blueprint(dashboard_bp)

    # Error Handlers conforming to Section 20
    @app.errorhandler(404)
    def not_found(e):
        return jsonify({
            "success": False,
            "error": {
                "code": "NOT_FOUND",
                "message": "The requested endpoint or resource was not found on DhilipHome Server.",
            }
        }), 404

    @app.errorhandler(405)
    def method_not_allowed(e):
        return jsonify({
            "success": False,
            "error": {
                "code": "METHOD_NOT_ALLOWED",
                "message": "The HTTP method is not allowed for this endpoint.",
            }
        }), 405

    @app.errorhandler(413)
    def file_too_large(e):
        return jsonify({
            "success": False,
            "error": {
                "code": "FILE_TOO_LARGE",
                "message": f"File exceeds maximum upload size of {Config.MAX_CONTENT_LENGTH // (1024 * 1024)} MB.",
            }
        }), 413

    @app.errorhandler(500)
    def internal_error(e):
        app.logger.error(f"Internal server error: {e}")
        return jsonify({
            "success": False,
            "error": {
                "code": "INTERNAL_SERVER_ERROR",
                "message": "An unexpected server error occurred.",
            }
        }), 500

    # WebSocket Event Handlers
    @socketio.on("connect")
    def on_connect():
        app.logger.info("Realtime WebSocket client connected")
        from app.services.system_service import SystemService
        socketio.emit("server_status", {
            "status": "ONLINE",
            "server": Config.NAME,
            "version": Config.VERSION,
            "timestamp": time.time(),
        })

    @socketio.on("disconnect")
    def on_disconnect():
        app.logger.info("Realtime WebSocket client disconnected")

    # Start background workers
    start_udp_discovery_worker(app)
    start_system_telemetry_worker(app)

    return app
