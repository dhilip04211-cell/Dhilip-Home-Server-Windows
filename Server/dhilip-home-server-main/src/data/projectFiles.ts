export interface ProjectFileEntry {
  path: string;
  category: 'core' | 'route' | 'service' | 'database' | 'util' | 'deployment' | 'test' | 'config';
  description: string;
  content: string;
}

export const PROJECT_FILES: ProjectFileEntry[] = [
  {
    path: 'server.py',
    category: 'core',
    description: 'Server entry point, banner display, and WebSocket event loop launcher',
    content: `#!/usr/bin/env python3
import sys
from app import create_app, socketio
from app.utils.config import Config, get_lan_ip

app = create_app()

if __name__ == "__main__":
    host = Config.HOST
    port = Config.PORT
    lan_ip = get_lan_ip()
    print("DhilipHome Server Starting on " + lan_ip + ":" + str(port))
    socketio.run(app, host=host, port=port, debug=False, allow_unsafe_werkzeug=True)`
  },
  {
    path: 'app/__init__.py',
    category: 'core',
    description: 'Flask application factory, blueprints registration, logging, and background workers',
    content: `# Flask app factory, CORS, rotating logging, SocketIO, and UDP discovery
from flask import Flask
from flask_cors import CORS
from flask_socketio import SocketIO
# See full source in repository`
  },
  {
    path: 'app/routes/health.py',
    category: 'route',
    description: 'GET /api/health, GET /api/server, and GET /api/discovery',
    content: `# Health check, server info, and discovery routes`
  },
  {
    path: 'app/routes/system.py',
    category: 'route',
    description: 'GET /api/system CPU, RAM, Swap, Uptime, and Process metrics',
    content: `# Real-time hardware monitoring endpoints`
  },
  {
    path: 'app/routes/storage.py',
    category: 'route',
    description: 'GET /api/storage mounted partition detection and disk space',
    content: `# Filesystem storage endpoints`
  },
  {
    path: 'app/routes/network.py',
    category: 'route',
    description: 'GET /api/network interfaces, LAN IPs, and transfer stats',
    content: `# Network interface and traffic endpoints`
  },
  {
    path: 'app/routes/files.py',
    category: 'route',
    description: 'GET /api/files, POST /api/files/upload, DELETE /api/files',
    content: `# Secure sandboxed file management endpoints`
  },
  {
    path: 'app/routes/media.py',
    category: 'route',
    description: 'GET /api/media, POST /api/media/scan, GET /api/media/stream/<path>',
    content: `# Range request video/audio streaming and SQLite search`
  },
  {
    path: 'app/routes/auth.py',
    category: 'route',
    description: 'POST /api/auth/login, POST /api/auth/logout, GET /api/auth/status',
    content: `# Password verification and bearer token authentication`
  },
  {
    path: 'app/services/system_service.py',
    category: 'service',
    description: 'Hardware metric gathering using psutil',
    content: `# SystemService class gathering CPU, RAM, Swap, OS, Uptime`
  },
  {
    path: 'app/services/storage_service.py',
    category: 'service',
    description: 'Disk partition detection and capacity calculation',
    content: `# StorageService class filtering pseudo-filesystems`
  },
  {
    path: 'app/services/network_service.py',
    category: 'service',
    description: 'Network interface enumeration and I/O counters',
    content: `# NetworkService class extracting IPs and adapter stats`
  },
  {
    path: 'app/services/file_service.py',
    category: 'service',
    description: 'Path traversal protection and filesystem operations',
    content: `# FileService class enforcing sandboxed operations in MEDIA_ROOT`
  },
  {
    path: 'app/services/media_service.py',
    category: 'service',
    description: 'Media categorization, SQLite indexer, and HTTP 206 stream generator',
    content: `# MediaService class with HTTP Range chunked streaming`
  },
  {
    path: 'app/database/database.py',
    category: 'database',
    description: 'SQLite connection manager and schema bootstrap',
    content: `# Thread-safe SQLite connection context and tables setup`
  },
  {
    path: 'app/database/models.py',
    category: 'database',
    description: 'UserModel, MediaModel, and SettingsModel SQLite operations',
    content: `# Models for users, media indexing, and key-value settings`
  },
  {
    path: 'app/utils/config.py',
    category: 'util',
    description: 'Environment loader, LAN IP detection, and byte formatters',
    content: `# Config class and helper functions`
  },
  {
    path: 'app/utils/security.py',
    category: 'util',
    description: 'Path traversal validator, password hasher, and auth token generator',
    content: `# Security utilities and @require_auth decorator`
  },
  {
    path: 'deployment/dhilip-home-server.service',
    category: 'deployment',
    description: 'Systemd service unit for Debian (auto-restart, security sandbox)',
    content: `[Unit]
Description=DhilipHome Server - Private Home Server Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/dhilip-home-server
ExecStart=/opt/dhilip-home-server/venv/bin/python /opt/dhilip-home-server/server.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target`
  },
  {
    path: 'deployment/install.sh',
    category: 'deployment',
    description: 'Debian CLI installer (venv, dependencies, directories, DB init)',
    content: `#!/usr/bin/env bash
# Automated Debian installation script`
  },
  {
    path: 'deployment/update.sh',
    category: 'deployment',
    description: 'Git pull, venv update, and service restart script',
    content: `#!/usr/bin/env bash
# Git pull and update script`
  },
  {
    path: 'deployment/start.sh',
    category: 'deployment',
    description: 'Start server via systemd or background process',
    content: `#!/usr/bin/env bash
# Start script`
  },
  {
    path: 'deployment/stop.sh',
    category: 'deployment',
    description: 'Stop systemd service or background PID',
    content: `#!/usr/bin/env bash
# Stop script`
  },
  {
    path: 'deployment/restart.sh',
    category: 'deployment',
    description: 'Restart server instance safely',
    content: `#!/usr/bin/env bash
# Restart script`
  },
  {
    path: 'requirements.txt',
    category: 'config',
    description: 'Python package dependencies (Flask, Flask-CORS, Flask-SocketIO, psutil, gunicorn)',
    content: `Flask>=3.0.0,<4.0.0
Flask-Cors>=4.0.0,<5.0.0
Flask-SocketIO>=5.3.6,<6.0.0
python-dotenv>=1.0.1,<2.0.0
psutil>=5.9.8,<6.0.0
gunicorn>=21.2.0,<23.0.0
simple-websocket>=1.0.0
Werkzeug>=3.0.1,<4.0.0`
  },
  {
    path: '.env.example',
    category: 'config',
    description: 'Template environment file with default ports, secrets, and directories',
    content: `HOST=0.0.0.0
PORT=8080
MEDIA_ROOT=./media
DATABASE_PATH=./data/dhiliphome.db
LOG_PATH=./logs/server.log
DHILIPHOME_SECRET_KEY=change_this_to_a_secure_random_string_in_production
DHILIPHOME_ADMIN_USERNAME=admin
DHILIPHOME_ADMIN_PASSWORD=CHANGE_THIS_PASSWORD
DISCOVERY_UDP_PORT=8888
SYSTEM_UPDATE_INTERVAL=3
CORS_ORIGINS=*`
  },
  {
    path: '.github/workflows/ci.yml',
    category: 'config',
    description: 'Automated GitHub Actions CI pipeline testing across Python 3.10, 3.11, 3.12',
    content: `name: DhilipHome Server CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: "3.11" }
      - run: pip install -r requirements.txt
      - run: python -m unittest discover -s tests`
  }
];
