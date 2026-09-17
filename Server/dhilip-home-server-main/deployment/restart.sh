#!/usr/bin/env bash
# ==============================================================================
# DHILIPHOME SERVER — RESTART SCRIPT
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if systemctl list-unit-files | grep -q "dhilip-home-server.service" 2>/dev/null; then
    echo "Restarting via systemd..."
    sudo systemctl restart dhilip-home-server
    sudo systemctl status dhilip-home-server --no-pager
    exit 0
fi

"${SCRIPT_DIR}/stop.sh"
sleep 1
"${SCRIPT_DIR}/start.sh"
