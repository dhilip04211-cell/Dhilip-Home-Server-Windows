#!/usr/bin/env bash
# ==============================================================================
# DHILIPHOME SERVER — DEBIAN INSTALLATION SCRIPT
# Installs Python dependencies, sets up virtualenv, initializes directories and DB.
# ==============================================================================

set -e

# Detect script and project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================="
echo "    DHILIPHOME SERVER — DEBIAN INSTALLER          "
echo "=================================================="
echo "Project Directory: ${PROJECT_DIR}"

# 1. Check Python 3
echo -n "[1/7] Checking Python 3 installation... "
if ! command -v python3 >/dev/null 2>&1; then
    echo "FAILED"
    echo "Error: Python 3 is not installed."
    echo "Install it with: sudo apt update && sudo apt install -y python3 python3-venv python3-pip"
    exit 1
fi
PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
echo "OK (Python ${PYTHON_VERSION})"

# 2. Check for python3-venv package capability
echo -n "[2/7] Checking venv module capability... "
if ! python3 -m venv --help >/dev/null 2>&1; then
    echo "FAILED"
    echo "Error: python3-venv is missing."
    echo "Install it with: sudo apt update && sudo apt install -y python3-venv python3-pip"
    exit 1
fi
echo "OK"

# 3. Create necessary runtime directories
echo -n "[3/7] Creating runtime storage directories... "
mkdir -p "${PROJECT_DIR}/data"
mkdir -p "${PROJECT_DIR}/media"
mkdir -p "${PROJECT_DIR}/logs"
mkdir -p "${PROJECT_DIR}/config"
echo "OK"

# 4. Create virtual environment
echo -n "[4/7] Setting up Python virtual environment (venv)... "
if [ ! -d "${PROJECT_DIR}/venv" ]; then
    python3 -m venv "${PROJECT_DIR}/venv"
fi
source "${PROJECT_DIR}/venv/bin/activate"
pip install --quiet --upgrade pip setuptools wheel
echo "OK"

# 5. Install requirements
echo "[5/7] Installing requirements from requirements.txt..."
pip install -r "${PROJECT_DIR}/requirements.txt"

# 6. Initialize .env configuration if absent
echo -n "[6/7] Checking .env configuration... "
if [ ! -f "${PROJECT_DIR}/.env" ]; then
    cp "${PROJECT_DIR}/.env.example" "${PROJECT_DIR}/.env"
    # Generate random secret key
    RANDOM_SECRET=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/change_this_to_a_secure_random_string_in_production/${RANDOM_SECRET}/" "${PROJECT_DIR}/.env"
    else
        sed -i "s/change_this_to_a_secure_random_string_in_production/${RANDOM_SECRET}/" "${PROJECT_DIR}/.env"
    fi
    echo "CREATED (.env generated from .env.example with secure secret)"
else
    echo "FOUND (Existing .env preserved)"
fi

# 7. Initialize SQLite Database
echo -n "[7/7] Initializing SQLite database schema... "
python3 -c "from app import create_app; create_app()"
echo "OK"

# Detect IP
PRIMARY_IP=$(python3 -c "import socket; s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.connect(('8.8.8.8', 80)); print(s.getsockname()[0]); s.close()" 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")

echo ""
echo "=================================================="
echo "    INSTALLATION COMPLETED SUCCESSFULLY!          "
echo "=================================================="
echo ""
echo "Start the server manually:"
echo "  source venv/bin/activate"
echo "  python3 server.py"
echo ""
echo "Or install and start as a Debian systemd service:"
echo "  sudo cp deployment/dhilip-home-server.service /etc/systemd/system/"
echo "  sudo sed -i \"s|/opt/dhilip-home-server|${PROJECT_DIR}|g\" /etc/systemd/system/dhilip-home-server.service"
echo "  sudo systemctl daemon-reload"
echo "  sudo systemctl enable dhilip-home-server"
echo "  sudo systemctl start dhilip-home-server"
echo ""
echo "Server will be reachable at:"
echo "  http://${PRIMARY_IP}:8080/api/health"
echo "=================================================="
