#!/usr/bin/env python3
"""
DhilipHome Server - Entry Point
Launches the Flask application and WebSocket server with automatic LAN detection.
"""

import sys
from app import create_app, socketio
from app.utils.config import Config, get_lan_ip


def print_startup_banner(lan_ip: str, port: int):
    """Prints the official DhilipHome Server terminal banner."""
    local_url = f"http://127.0.0.1:{port}"
    lan_url = f"http://{lan_ip}:{port}"
    health_url = f"http://{lan_ip}:{port}/api/health"
    system_url = f"http://{lan_ip}:{port}/api/system"
    discovery_url = f"http://{lan_ip}:{port}/api/discovery"

    banner = f"""
==================================================
        DHILIPHOME SERVER
==================================================

Version : {Config.VERSION}
Status  : ONLINE

Local:
{local_url}

LAN:
{lan_url}

Health:
{health_url}

System:
{system_url}

Discovery:
{discovery_url}

Storage Root:
{Config.MEDIA_ROOT}

Database:
{Config.DATABASE_PATH}

Logs:
{Config.LOG_PATH}

==================================================
"""
    print(banner, flush=True)


# Create Flask application instance
app = create_app()

def run_server():
    host = Config.HOST
    port = Config.PORT
    lan_ip = get_lan_ip()

    print_startup_banner(lan_ip, port)

    try:
        # socketio.run provides production-grade event loop and WebSocket support
        socketio.run(
            app,
            host=host,
            port=port,
            debug=False,
            allow_unsafe_werkzeug=True,
        )
    except KeyboardInterrupt:
        print("\nShutting down DhilipHome Server gracefully...", flush=True)
        sys.exit(0)
    except Exception as e:
        print(f"\nFatal error starting DhilipHome Server: {e}", file=sys.stderr, flush=True)
        sys.exit(1)


if __name__ == "__main__":
    run_server()
