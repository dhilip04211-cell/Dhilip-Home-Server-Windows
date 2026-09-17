#!/usr/bin/env bash
# ==============================================================================
# DHILIPHOME SERVER — UPDATE SCRIPT
# Safely pulls the latest code from private GitHub repo, updates dependencies, and restarts.
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================="
echo "    DHILIPHOME SERVER — PULLING UPDATES           "
echo "=================================================="
cd "${PROJECT_DIR}"

# 1. Pull latest commits from GitHub
echo "[1/4] Pulling latest code from Git..."
if [ -d ".git" ]; then
    git pull origin main || git pull origin master
else
    echo "Warning: Not a git repository. Skipping git pull."
fi

# 2. Activate virtual environment
echo "[2/4] Activating virtual environment..."
if [ -d "venv" ]; then
    source venv/bin/activate
else
    echo "Error: Virtual environment not found. Run ./deployment/install.sh first."
    exit 1
fi

# 3. Update pip dependencies
echo "[3/4] Checking and installing dependency updates..."
pip install --quiet --upgrade pip
pip install --quiet -r requirements.txt

# Re-run schema initialization for potential new tables
python3 -c "from app import create_app; create_app()"

# 4. Restart server service
echo "[4/4] Restarting DhilipHome Server..."
"${SCRIPT_DIR}/restart.sh"

echo "=================================================="
echo "    UPDATE COMPLETED SUCCESSFULLY!                "
echo "=================================================="
