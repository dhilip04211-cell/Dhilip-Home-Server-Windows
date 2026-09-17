#!/usr/bin/env bash
# ==============================================================================
# DHILIPHOME SERVER — STOP SCRIPT
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${PROJECT_DIR}/logs/server.pid"

if systemctl is-active --quiet dhilip-home-server 2>/dev/null; then
    echo "Stopping systemd service..."
    sudo systemctl stop dhilip-home-server
    echo "Service stopped."
    exit 0
fi

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping process PID ${PID}..."
        kill -15 "$PID"
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID"
        fi
        rm -f "$PID_FILE"
        echo "Server stopped successfully."
        exit 0
    else
        rm -f "$PID_FILE"
        echo "Process was not running. Stale PID file cleaned up."
        exit 0
    fi
fi

echo "No running DhilipHome Server found."
