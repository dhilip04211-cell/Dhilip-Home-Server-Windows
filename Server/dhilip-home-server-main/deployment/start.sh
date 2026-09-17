#!/usr/bin/env bash
# ==============================================================================
# DHILIPHOME SERVER — START SCRIPT
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${PROJECT_DIR}/logs/server.pid"

# Check if systemd unit is active
if systemctl is-active --quiet dhilip-home-server 2>/dev/null; then
    echo "DhilipHome Server is already running as a systemd service."
    exit 0
fi

if systemctl list-unit-files | grep -q "dhilip-home-server.service" 2>/dev/null; then
    echo "Starting via systemd..."
    sudo systemctl start dhilip-home-server
    sudo systemctl status dhilip-home-server --no-pager
    exit 0
fi

# Fallback to standalone background process
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "DhilipHome Server is already running with PID ${PID}."
        exit 0
    else
        rm -f "$PID_FILE"
    fi
fi

if [ ! -d "${PROJECT_DIR}/venv" ]; then
    echo "Virtual environment missing! Run ./deployment/install.sh first."
    exit 1
fi

echo "Starting DhilipHome Server in background..."
source "${PROJECT_DIR}/venv/bin/activate"
nohup python3 "${PROJECT_DIR}/server.py" >> "${PROJECT_DIR}/logs/server.log" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
echo "Server started with PID ${NEW_PID}."
echo "View logs with: tail -f logs/server.log"
