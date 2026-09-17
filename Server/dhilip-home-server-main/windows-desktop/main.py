import json
import os
import sys
import threading
from pathlib import Path

import requests
from PySide6.QtCore import Qt, QTimer, QUrl
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

APP_NAME = "Dhilip Home"
DEFAULT_PORT = int(os.getenv("DHILIPHOME_PORT", "8080"))
APP_DATA_DIR = Path.home() / ".dhiliphome"
APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
SETTINGS_PATH = APP_DATA_DIR / "settings.json"


def start_server():
    """Start the existing Flask server in-process so Android clients and the Windows UI share one backend."""
    try:
        from app import create_app, socketio
        from app.utils.config import Config

        flask_app = create_app()
        socketio.run(
            flask_app,
            host=Config.HOST,
            port=Config.PORT,
            debug=False,
            allow_unsafe_werkzeug=True,
            use_reloader=False,
        )
    except Exception as exc:  # pragma: no cover - runtime startup path
        print(f"[DhilipHome] Server startup failed: {exc}", file=sys.stderr)
        raise


class SettingsStore:
    @staticmethod
    def load():
        if not SETTINGS_PATH.exists():
            return {"server_url": f"http://127.0.0.1:{DEFAULT_PORT}", "theme": "dark"}
        try:
            with SETTINGS_PATH.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
                return {
                    "server_url": data.get("server_url", f"http://127.0.0.1:{DEFAULT_PORT}"),
                    "theme": data.get("theme", "dark"),
                }
        except Exception:
            return {"server_url": f"http://127.0.0.1:{DEFAULT_PORT}", "theme": "dark"}

    @staticmethod
    def save(data):
        with SETTINGS_PATH.open("w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2)


class AuthManager:
    def __init__(self):
        self.token_file = APP_DATA_DIR / "auth_token.txt"

    def get_token(self):
        if self.token_file.exists():
            try:
                token = self.token_file.read_text(encoding="utf-8").strip()
                if token:
                    return token
            except Exception:
                pass
        return None

    def set_token(self, token):
        if token:
            self.token_file.write_text(token, encoding="utf-8")

    def clear(self):
        try:
            self.token_file.unlink(missing_ok=True)
        except Exception:
            pass


class ApiClient:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = AuthManager().get_token()
        self.session = requests.Session()

    def headers(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def request(self, method, path, **kwargs):
        url = f"{self.base_url}{path}"
        headers = kwargs.pop("headers", {})
        headers.update(self.headers())
        kwargs["headers"] = headers
        kwargs.setdefault("timeout", 15)
        return self.session.request(method, url, **kwargs)

    def get(self, path, **kwargs):
        return self.request("GET", path, **kwargs)

    def post(self, path, **kwargs):
        return self.request("POST", path, **kwargs)

    def delete(self, path, **kwargs):
        return self.request("DELETE", path, **kwargs)

    def login(self, username, password):
        response = self.post("/api/auth/login", json={"username": username, "password": password})
        if not response.ok:
            return False, response.text
        try:
            payload = response.json()
            token = payload.get("data", {}).get("token") or payload.get("token")
            if token:
                self.token = token
                AuthManager().set_token(token)
                return True, "Connected"
        except Exception:
            pass
        return False, "Authentication response was invalid"

    def auth_status(self):
        response = self.get("/api/auth/status")
        try:
            return response.json()
        except Exception:
            return {"authenticated": False, "user": None}


class DhilipHomeWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.settings = SettingsStore.load()
        self.api = ApiClient(self.settings["server_url"])
        self.current_path = ""
        self.media_player = QMediaPlayer(self)
        self.audio_output = QAudioOutput(self)
        self.media_player.setAudioOutput(self.audio_output)
        self.video_widget = QVideoWidget()
        self.media_player.setVideoOutput(self.video_widget)
        self.nav_index = {}
        self._build_ui()
        self.refresh_timer = QTimer(self)
        self.refresh_timer.timeout.connect(self.refresh_server_state)
        self.refresh_timer.start(5000)
        QTimer.singleShot(800, self.refresh_server_state)

    def _build_ui(self):
        self.setWindowTitle(APP_NAME)
        self.resize(1420, 860)
        self.setMinimumSize(1180, 720)

        central = QWidget()
        root = QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        sidebar = QFrame()
        sidebar.setObjectName("sidebar")
        sidebar.setFixedWidth(240)
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(20, 18, 20, 16)
        sidebar_layout.setSpacing(12)

        brand = QLabel("DHILIP HOME")
        brand.setObjectName("brand")
        sidebar_layout.addWidget(brand)

        nav_sections = [
            ("Home", "home"),
            ("Files", "files"),
            ("Media", "media"),
            ("Downloads", "downloads"),
            ("Cloud", "cloud"),
            ("Settings", "settings"),
        ]

        self.nav = QStackedWidget()
        self.home_page = self._build_home_page()
        self.files_page = self._build_files_page()
        self.media_page = self._build_media_page()
        self.downloads_page = self._build_downloads_page()
        self.cloud_page = self._build_cloud_page()
        self.settings_page = self._build_settings_page()

        self.nav.addWidget(self.home_page)
        self.nav.addWidget(self.files_page)
        self.nav.addWidget(self.media_page)
        self.nav.addWidget(self.downloads_page)
        self.nav.addWidget(self.cloud_page)
        self.nav.addWidget(self.settings_page)

        for idx, (label, name) in enumerate(nav_sections):
            button = QPushButton(label)
            button.setObjectName("navButton")
            self.nav_index[name] = idx
            button.clicked.connect(lambda _, page_index=idx: self.nav.setCurrentIndex(page_index))
            sidebar_layout.addWidget(button)

        sidebar_layout.addStretch()
        self.status_badge = QLabel("Server starting")
        self.status_badge.setObjectName("statusBadge")
        sidebar_layout.addWidget(self.status_badge)

        root.addWidget(sidebar)
        root.addWidget(self.nav, 1)
        self.setCentralWidget(central)
        self.apply_theme(self.settings.get("theme", "dark"))

    def _panel(self, title, subtitle):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(18)

        heading = QLabel(title)
        heading.setObjectName("pageTitle")
        layout.addWidget(heading)

        sub = QLabel(subtitle)
        sub.setObjectName("pageSubtitle")
        layout.addWidget(sub)
        return page, layout

    def _build_home_page(self):
        page, layout = self._panel("Home", "Real server status and local network overview")
        self.home_cards = QWidget()
        card_layout = QHBoxLayout(self.home_cards)
        card_layout.setSpacing(18)
        card_layout.setContentsMargins(0, 0, 0, 0)

        self.home_card_labels = {}
        for title, key in [
            ("Server", "status"),
            ("Storage", "storage"),
            ("Downloads", "downloads"),
            ("Devices", "devices"),
            ("Media", "media"),
            ("Network", "network"),
        ]:
            frame = QFrame()
            frame.setObjectName("infoCard")
            frame_layout = QVBoxLayout(frame)
            frame_layout.setContentsMargins(18, 16, 18, 16)
            title_label = QLabel(title)
            title_label.setObjectName("cardTitle")
            value_label = QLabel("Unavailable")
            value_label.setObjectName("cardValue")
            value_label.setWordWrap(True)
            frame_layout.addWidget(title_label)
            frame_layout.addWidget(value_label)
            self.home_card_labels[key] = value_label
            card_layout.addWidget(frame)

        layout.addWidget(self.home_cards)
        layout.addStretch()
        return page

    def _build_files_page(self):
        page, layout = self._panel("Files", "Browse, upload, and manage media and documents")
        toolbar = QHBoxLayout()
        self.path_input = QLineEdit()
        self.path_input.setPlaceholderText("Current folder")
        open_button = QPushButton("Open")
        open_button.clicked.connect(lambda: self.load_files(self.path_input.text()))
        upload_button = QPushButton("Upload")
        upload_button.clicked.connect(self.upload_file)
        folder_button = QPushButton("New Folder")
        folder_button.clicked.connect(self.create_folder)
        toolbar.addWidget(self.path_input, 1)
        toolbar.addWidget(open_button)
        toolbar.addWidget(upload_button)
        toolbar.addWidget(folder_button)
        layout.addLayout(toolbar)

        self.file_list = QListWidget()
        self.file_list.itemDoubleClicked.connect(self.open_file_or_directory)
        layout.addWidget(self.file_list, 1)
        return page

    def _build_media_page(self):
        page, layout = self._panel("Media", "Your home media library and playback queue")
        self.media_list = QListWidget()
        self.media_list.itemDoubleClicked.connect(self.open_media_item)
        layout.addWidget(self.media_list, 1)
        return page

    def _build_downloads_page(self):
        page, layout = self._panel("Downloads", "Real server-managed remote download state")
        self.download_list = QListWidget()
        layout.addWidget(self.download_list, 1)
        return page

    def _build_cloud_page(self):
        page, layout = self._panel("Cloud", "Send a remote URL to the server for direct download")
        form = QWidget()
        form_layout = QVBoxLayout(form)
        form_layout.setContentsMargins(0, 0, 0, 0)
        form_layout.setSpacing(12)

        self.cloud_url = QLineEdit()
        self.cloud_url.setPlaceholderText("https://example.com/file.mp4")
        self.cloud_destination = QLineEdit()
        self.cloud_destination.setPlaceholderText("Movies")
        self.cloud_submit = QPushButton("Start download")
        self.cloud_submit.clicked.connect(self.start_cloud_download)

        form_layout.addWidget(QLabel("URL"))
        form_layout.addWidget(self.cloud_url)
        form_layout.addWidget(QLabel("Destination"))
        form_layout.addWidget(self.cloud_destination)
        form_layout.addWidget(self.cloud_submit)
        form_layout.addStretch()
        layout.addWidget(form)
        return page

    def _build_settings_page(self):
        page, layout = self._panel("Settings", "Server connection, appearance and app configuration")
        settings_frame = QWidget()
        settings_layout = QVBoxLayout(settings_frame)
        settings_layout.setSpacing(12)

        self.server_url_input = QLineEdit(self.settings.get("server_url", f"http://127.0.0.1:{DEFAULT_PORT}"))
        self.server_url_input.setPlaceholderText("http://192.168.1.42:8080")
        login_button = QPushButton("Connect")
        login_button.clicked.connect(self.connect_to_server)
        self.theme_combo = QLineEdit(self.settings.get("theme", "dark"))
        self.settings_message = QLabel("Ready")
        self.settings_message.setObjectName("inlineMessage")

        row = QHBoxLayout()
        row.addWidget(self.server_url_input, 1)
        row.addWidget(login_button)
        settings_layout.addWidget(QLabel("Server URL"))
        settings_layout.addLayout(row)
        settings_layout.addWidget(QLabel("Theme"))
        settings_layout.addWidget(self.theme_combo)
        settings_layout.addWidget(self.settings_message)
        settings_layout.addStretch()
        layout.addWidget(settings_frame)
        return page

    def apply_theme(self, theme_name):
        theme = (theme_name or "dark").lower()
        dark_styles = """
            QMainWindow { background: #0B0E14; color: #F4F7FB; }
            #sidebar { background: #121722; border: 1px solid #273040; border-radius: 18px; }
            #brand { color: #F4F7FB; font-size: 26px; font-weight: 700; letter-spacing: 1.2px; }
            #navButton { background: #181F2C; color: #F4F7FB; border: 1px solid #273040; border-radius: 12px; padding: 12px 14px; text-align: left; }
            #navButton:hover { background: #1F2940; }
            #statusBadge { color: #35D07F; background: rgba(53, 208, 127, 0.12); border: 1px solid rgba(53, 208, 127, 0.25); border-radius: 10px; padding: 10px 12px; }
            #pageTitle { font-size: 30px; font-weight: 650; color: #F4F7FB; }
            #pageSubtitle { color: #9AA5B5; font-size: 14px; }
            #infoCard { background: #121722; border: 1px solid #273040; border-radius: 16px; }
            #cardTitle { color: #9AA5B5; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; }
            #cardValue { color: #F4F7FB; font-size: 18px; }
            QLabel, QLineEdit, QListWidget, QPushButton { color: #F4F7FB; }
            QLineEdit, QListWidget { background: #0F1724; border: 1px solid #273040; border-radius: 10px; padding: 10px 12px; }
            QPushButton { background: #181F2C; border: 1px solid #273040; border-radius: 10px; padding: 10px 14px; }
            QPushButton:hover { background: #1E2635; }
            QListWidget::item { border-radius: 8px; padding: 10px; }
            QListWidget::item:selected { background: #201E3D; }
            #inlineMessage { color: #8B78FF; }
        """
        light_styles = """
            QMainWindow { background: #F2F5FA; color: #111827; }
            #sidebar { background: #FFFFFF; border: 1px solid #D9E2F0; border-radius: 18px; }
            #brand { color: #1F2937; font-size: 26px; font-weight: 700; letter-spacing: 1.2px; }
            #navButton { background: #EEF4FF; color: #1F2937; border: 1px solid #D9E2F0; border-radius: 12px; padding: 12px 14px; }
            #navButton:hover { background: #E2EBFF; }
            #statusBadge { color: #0C8C5D; background: rgba(53, 208, 127, 0.10); border: 1px solid rgba(53, 208, 127, 0.25); border-radius: 10px; padding: 10px 12px; }
            #pageTitle { font-size: 30px; font-weight: 650; color: #111827; }
            #pageSubtitle { color: #58657A; font-size: 14px; }
            #infoCard { background: #FFFFFF; border: 1px solid #D9E2F0; border-radius: 16px; }
            #cardTitle { color: #58657A; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; }
            #cardValue { color: #111827; font-size: 18px; }
            QLabel, QLineEdit, QListWidget, QPushButton { color: #111827; }
            QLineEdit, QListWidget { background: #F9FAFC; border: 1px solid #D9E2F0; border-radius: 10px; padding: 10px 12px; }
            QPushButton { background: #EEF4FF; border: 1px solid #D9E2F0; border-radius: 10px; padding: 10px 14px; }
            QPushButton:hover { background: #E3ECFF; }
            QListWidget::item { border-radius: 8px; padding: 10px; }
            QListWidget::item:selected { background: #DDE8FF; }
            #inlineMessage { color: #4F46E5; }
        """
        self.setStyleSheet(dark_styles if theme == "dark" else light_styles)
        self.settings["theme"] = theme
        SettingsStore.save(self.settings)

    def refresh_server_state(self):
        try:
            response = self.api.get("/api/health")
            if response.ok:
                payload = response.json()
                status = payload.get("status", "ok")
                self.status_badge.setText("Server online")
                self.status_badge.setStyleSheet("color: #35D07F; background: rgba(53, 208, 127, 0.12); border: 1px solid rgba(53, 208, 127, 0.25); border-radius: 10px; padding: 10px 12px;")
                self._update_home_cards(status)
                if self.nav.currentIndex() == self.nav_index["files"]:
                    self.load_files()
                if self.nav.currentIndex() == self.nav_index["media"]:
                    self.load_media_catalog()
                if self.nav.currentIndex() == self.nav_index["downloads"]:
                    self.load_download_items()
            else:
                self.status_badge.setText("Server unavailable")
                self.status_badge.setStyleSheet("color: #F4B860; background: rgba(244, 184, 96, 0.10); border: 1px solid rgba(244, 184, 96, 0.25); border-radius: 10px; padding: 10px 12px;")
                self._set_home_value("status", "Unavailable")
        except Exception:
            self.status_badge.setText("Connecting")
            self.status_badge.setStyleSheet("color: #F4B860; background: rgba(244, 184, 96, 0.10); border: 1px solid rgba(244, 184, 96, 0.25); border-radius: 10px; padding: 10px 12px;")
            self._set_home_value("status", "Unavailable")

    def _set_home_value(self, key, value):
        if key in self.home_card_labels:
            self.home_card_labels[key].setText(str(value))

    def _update_home_cards(self, status):
        try:
            health = self.api.get("/api/health").json()
            system = self.api.get("/api/system").json()
            network = self.api.get("/api/network").json()
            server = self.api.get("/api/server").json()
            self._set_home_value("status", f"{health.get('server', 'DhilipHome Server')}\n{status.upper()}")
            self._set_home_value("storage", f"{system.get('memory', {}).get('used_human', 'Unavailable')} / {system.get('memory', {}).get('total_human', 'Unavailable')}")
            self._set_home_value("downloads", "Active downloads from server")
            self._set_home_value("devices", "Connected devices visible from server")
            self._set_home_value("media", f"Indexed media catalog\n{health.get('version', 'Unknown')}")
            self._set_home_value("network", f"{server.get('ip', 'Unavailable')}:{server.get('port', '8080')}\n{network.get('interfaces', [{}])[0].get('ip_address', 'LAN unavailable')}")
        except Exception:
            self._set_home_value("status", "Unavailable")

    def connect_to_server(self):
        url = self.server_url_input.text().strip()
        if not url:
            self.settings_message.setText("Enter a server URL first")
            return
        self.api = ApiClient(url)
        self.settings["server_url"] = url
        SettingsStore.save(self.settings)
        self.settings_message.setText("Connected to server")
        self.refresh_server_state()

    def load_files(self, path=""):
        if not path:
            path = self.current_path
        try:
            response = self.api.get("/api/files/list", params={"path": path})
            if not response.ok:
                self.file_list.clear()
                self.file_list.addItem("Unable to load the server file list")
                return
            payload = response.json().get("data", response.json())
            self.current_path = payload.get("current_path", "")
            self.path_input.setText(self.current_path)
            self.file_list.clear()
            for folder in payload.get("directories", []):
                item = QListWidgetItem(f"Folder: {folder['name']}")
                item.setData(Qt.UserRole, {"path": folder["path"], "is_dir": True})
                self.file_list.addItem(item)
            for file in payload.get("files", []):
                item = QListWidgetItem(f"File: {file['name']}")
                item.setData(Qt.UserRole, {"path": file["path"], "is_dir": False})
                self.file_list.addItem(item)
        except Exception:
            self.file_list.clear()
            self.file_list.addItem("Unable to connect to the server")

    def open_file_or_directory(self, item):
        payload = item.data(Qt.UserRole)
        if not payload:
            return
        if payload["is_dir"]:
            self.current_path = payload["path"]
            self.load_files(self.current_path)
            return
        self.open_media_item(item)

    def load_media_catalog(self):
        try:
            response = self.api.get("/api/media")
            if not response.ok:
                self.media_list.clear()
                self.media_list.addItem("Media catalog unavailable")
                return
            payload = response.json().get("data", response.json())
            self.media_list.clear()
            for item in payload.get("items", []):
                row = QListWidgetItem(f"{item.get('filename', 'Unknown')} ({item.get('category', 'Other')})")
                row.setData(Qt.UserRole, item)
                self.media_list.addItem(row)
        except Exception:
            self.media_list.clear()
            self.media_list.addItem("Unable to load media catalog")

    def open_media_item(self, item):
        payload = item.data(Qt.UserRole)
        if not payload:
            return

        media_path = payload.get("path") or payload.get("relative_path") or payload.get("filename")
        if not media_path:
            return

        stream_url = self.settings["server_url"].rstrip("/") + "/api/media/stream/" + media_path
        if self.api.token:
            stream_url = f"{stream_url}?token={self.api.token}"

        self.media_player.setSource(QUrl(stream_url))
        self.media_player.play()
        self.status_badge.setText(f"Playing {payload.get('filename', 'media')}")

    def load_download_items(self):
        try:
            response = self.api.get("/api/files/remote-download")
            if not response.ok:
                self.download_list.clear()
                self.download_list.addItem("No downloads available")
                return
            payload = response.json().get("data", response.json())
            self.download_list.clear()
            items = payload if isinstance(payload, list) else payload.get("items", [])
            for item in items:
                status = item.get("status", "unknown")
                progress = item.get("progress_percent", 0)
                label = f"{item.get('filename', 'download')} — {status.upper()} — {progress}%"
                row = QListWidgetItem(label)
                row.setData(Qt.UserRole, item)
                self.download_list.addItem(row)
        except Exception:
            self.download_list.clear()
            self.download_list.addItem("Unable to load download state")

    def start_cloud_download(self):
        url = self.cloud_url.text().strip()
        destination = self.cloud_destination.text().strip() or "Movies"
        if not url:
            QMessageBox.warning(self, "Cloud download", "Add a valid URL first")
            return
        try:
            response = self.api.post("/api/files/remote-download", json={"url": url, "destination": destination})
            if not response.ok:
                QMessageBox.warning(self, "Cloud download", response.text)
                return
            self.cloud_url.clear()
            self.cloud_destination.clear()
            self.download_list.clear()
            self.download_list.addItem("Download queued on the server")
        except Exception as exc:
            QMessageBox.warning(self, "Cloud download", str(exc))

    def upload_file(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "Select file to upload")
        if not file_path:
            return
        try:
            with open(file_path, "rb") as handle:
                response = self.api.post(
                    "/api/files/upload",
                    files={"file": (Path(file_path).name, handle)},
                    data={"path": self.current_path},
                )
            if not response.ok:
                QMessageBox.warning(self, "Upload", response.text)
                return
            self.load_files(self.current_path)
        except Exception as exc:
            QMessageBox.warning(self, "Upload", str(exc))

    def create_folder(self):
        folder_name, ok = QInputDialog.getText(self, "New folder", "Folder name")
        if not ok or not folder_name.strip():
            return
        try:
            response = self.api.post("/api/files/folder", json={"path": self.current_path, "name": folder_name.strip()})
            if not response.ok:
                QMessageBox.warning(self, "Folder", response.text)
                return
            self.load_files(self.current_path)
        except Exception as exc:
            QMessageBox.warning(self, "Folder", str(exc))


if __name__ == "__main__":
    threading.Thread(target=start_server, daemon=True).start()
    app = QApplication(sys.argv)
    window = DhilipHomeWindow()
    window.show()
    sys.exit(app.exec())
