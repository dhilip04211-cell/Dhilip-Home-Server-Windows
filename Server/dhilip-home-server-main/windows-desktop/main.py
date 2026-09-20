import json
import mimetypes
import os
import sys
import threading
import urllib.parse
import webbrowser
from pathlib import Path

import requests
from PySide6.QtCore import QEasingCurve, QPropertyAnimation, Qt, QTimer, QUrl
from PySide6.QtGui import QBrush, QColor, QDesktopServices, QIcon, QLinearGradient, QPainter, QPen, QPixmap
from PySide6.QtWidgets import QGraphicsOpacityEffect
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import (
    QApplication,
    QComboBox,
    QFileDialog,
    QFrame,
    QSlider,
    QGridLayout,
    QGroupBox,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSizePolicy,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

APP_NAME = "Dhilip Home"
DEFAULT_PORT = int(os.getenv("DHILIPHOME_PORT", "8080"))
APP_DATA_DIR = Path.home() / ".dhiliphome"
APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
SETTINGS_PATH = APP_DATA_DIR / "settings.json"


def make_app_icon(size=256):
    pixmap = QPixmap(size, size)
    pixmap.fill(Qt.GlobalColor.transparent)

    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)

    bg = QColor(9, 14, 27)
    glow = QColor(96, 165, 250)
    purple = QColor(99, 102, 241)
    cyan = QColor(34, 211, 238)
    mint = QColor(16, 185, 129)
    white = QColor(255, 255, 255)

    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QBrush(bg))
    painter.drawRoundedRect(12, 12, size - 24, size - 24, 56, 56)

    glow_brush = QBrush(QColor(80, 125, 255, 80))
    painter.setBrush(glow_brush)
    painter.drawEllipse(22, 18, size - 44, size - 44)

    painter.setBrush(QBrush(QColor(18, 24, 38, 210)))
    painter.drawRoundedRect(28, 28, size - 56, size - 56, 42, 42)

    grad = QLinearGradient(0, 0, size, size)
    grad.setColorAt(0.0, purple)
    grad.setColorAt(0.5, QColor(59, 130, 246))
    grad.setColorAt(1.0, mint)
    painter.setBrush(QBrush(grad))
    painter.drawRoundedRect(42, 42, size - 84, size - 84, 34, 34)

    reflection = QColor(255, 255, 255, 110)
    painter.setBrush(QBrush(reflection))
    painter.drawRoundedRect(54, 52, size - 108, 72, 26, 26)

    painter.setPen(QPen(QColor(255, 255, 255, 230), 12, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
    painter.drawLine(size // 2 - 18, 134, size // 2 + 18, 134)
    painter.drawLine(size // 2, 134, size // 2, 188)
    painter.drawLine(size // 2 - 28, 188, size // 2 + 28, 188)

    painter.setPen(QPen(QColor(255, 255, 255, 200), 10, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
    painter.drawArc(62, 90, 132, 132, 0, 360 * 16)

    painter.setPen(QPen(QColor(255, 255, 255, 100), 4, Qt.PenStyle.SolidLine))
    painter.drawArc(72, 100, 112, 112, 220 * 16, 90 * 16)

    painter.end()
    return QIcon(pixmap)


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
            return {"server_url": f"http://127.0.0.1:{DEFAULT_PORT}", "theme": "dark", "start_local_server": True}
        try:
            with SETTINGS_PATH.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
                return {
                    "server_url": data.get("server_url", f"http://127.0.0.1:{DEFAULT_PORT}"),
                    "theme": data.get("theme", "dark"),
                        "start_local_server": bool(data.get("start_local_server", True)),
                }
        except Exception:
                    return {"server_url": f"http://127.0.0.1:{DEFAULT_PORT}", "theme": "dark", "start_local_server": True}

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
        self.setWindowIcon(make_app_icon(128))
        self._build_ui()
        self.refresh_timer = QTimer(self)
        self.refresh_timer.timeout.connect(self.refresh_server_state)
        self.refresh_timer.start(5000)
        QTimer.singleShot(800, self.refresh_server_state)

    def _build_ui(self):
        self.setWindowTitle(APP_NAME)
        self.resize(1420, 860)
        self.setMinimumSize(1180, 720)
        self.setWindowFlags(Qt.WindowType.Window)

        central = QWidget()
        root = QHBoxLayout(central)
        root.setContentsMargins(20, 20, 20, 20)
        root.setSpacing(18)

        sidebar = QFrame()
        sidebar.setObjectName("sidebar")
        sidebar.setFixedWidth(248)
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(20, 20, 20, 16)
        sidebar_layout.setSpacing(12)

        brand = QLabel("DHILIP HOME")
        brand.setObjectName("brand")
        sidebar_layout.addWidget(brand)

        subtitle = QLabel("Smart home media hub")
        subtitle.setObjectName("sidebarSubtitle")
        sidebar_layout.addWidget(subtitle)

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

        self.top_bar = QWidget()
        self.top_bar.setObjectName("topBar")
        top_bar_layout = QHBoxLayout(self.top_bar)
        top_bar_layout.setContentsMargins(18, 12, 18, 12)
        top_bar_layout.setSpacing(12)

        self.media_dock = QFrame()
        self.media_dock.setObjectName("mediaDock")
        self.media_dock.setMinimumHeight(0)
        self.media_dock.setMaximumHeight(0)
        self.media_dock.hide()
        dock_layout = QHBoxLayout(self.media_dock)
        dock_layout.setContentsMargins(16, 12, 16, 12)
        dock_layout.setSpacing(16)

        self.now_playing_label = QLabel("No media playing")
        self.now_playing_label.setObjectName("nowPlayingTitle")
        self.now_playing_label.setWordWrap(True)

        self.media_meta_label = QLabel("Server playback")
        self.media_meta_label.setObjectName("mediaMeta")

        player_btns = QWidget()
        player_btns_layout = QHBoxLayout(player_btns)
        player_btns_layout.setContentsMargins(0, 0, 0, 0)
        self.media_prev_button = QPushButton("⏮")
        self.media_play_button = QPushButton("▶")
        self.media_pause_button = QPushButton("⏸")
        self.media_stop_button = QPushButton("■")
        self.media_next_button = QPushButton("⏭")
        for btn in [self.media_prev_button, self.media_play_button, self.media_pause_button, self.media_stop_button, self.media_next_button]:
            btn.setObjectName("miniMediaButton")
            btn.setFixedSize(36, 36)
        self.media_play_button.clicked.connect(self.play_selected_media)
        self.media_pause_button.clicked.connect(self.media_player.pause)
        self.media_stop_button.clicked.connect(self.media_player.stop)
        player_btns_layout.addWidget(self.media_prev_button)
        player_btns_layout.addWidget(self.media_play_button)
        player_btns_layout.addWidget(self.media_pause_button)
        player_btns_layout.addWidget(self.media_stop_button)
        player_btns_layout.addWidget(self.media_next_button)

        info_panel = QWidget()
        info_panel_layout = QVBoxLayout(info_panel)
        info_panel_layout.setContentsMargins(0, 0, 0, 0)
        info_panel_layout.addWidget(self.now_playing_label)
        info_panel_layout.addWidget(self.media_meta_label)

        self.video_widget.setMinimumSize(240, 120)
        self.video_widget.setMaximumSize(420, 180)
        self.video_widget.setObjectName("videoPreview")
        dock_layout.addWidget(info_panel, 1)
        dock_layout.addWidget(player_btns)
        dock_layout.addWidget(self.video_widget)

        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Search files or media")
        self.search_input.setClearButtonEnabled(True)
        self.search_input.returnPressed.connect(self.quick_search)

        self.playback_slider = QSlider(Qt.Orientation.Horizontal)
        self.playback_slider.setRange(0, 100)
        self.playback_slider.setValue(0)
        self.playback_slider.setObjectName("playbackSlider")
        self.playback_slider.valueChanged.connect(self.seek_media)

        self.refresh_button = QPushButton("Refresh")
        self.refresh_button.clicked.connect(self.refresh_all)
        self.theme_toggle_button = QPushButton("Toggle theme")
        self.theme_toggle_button.clicked.connect(self.toggle_theme)
        self.open_web_button = QPushButton("Open server")
        self.open_web_button.clicked.connect(self.open_server_web)

        top_bar_layout.addWidget(self.search_input, 1)
        top_bar_layout.addWidget(self.playback_slider, 2)
        top_bar_layout.addWidget(self.refresh_button)
        top_bar_layout.addWidget(self.theme_toggle_button)
        top_bar_layout.addWidget(self.open_web_button)

        content_panel = QWidget()
        content_panel.setObjectName("contentPanel")
        content_layout = QVBoxLayout(content_panel)
        content_layout.setContentsMargins(0, 0, 0, 0)
        content_layout.setSpacing(14)
        content_layout.addWidget(self.top_bar)
        content_layout.addWidget(self.media_dock)
        content_layout.addWidget(self.nav, 1)

        root.addWidget(sidebar)
        root.addWidget(content_panel, 1)
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
        card_layout = QGridLayout(self.home_cards)
        card_layout.setSpacing(18)
        card_layout.setContentsMargins(0, 0, 0, 0)

        self.home_card_labels = {}
        for index, (title, key) in enumerate([
            ("Server", "status"),
            ("Storage", "storage"),
            ("Downloads", "downloads"),
            ("Devices", "devices"),
            ("Media", "media"),
            ("Network", "network"),
        ]):
            frame = QFrame()
            frame.setObjectName("infoCard")
            frame.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Preferred)
            frame_layout = QVBoxLayout(frame)
            frame_layout.setContentsMargins(18, 16, 18, 16)
            title_label = QLabel(title)
            title_label.setObjectName("cardTitle")
            value_label = QLabel("Unavailable")
            value_label.setObjectName("cardValue")
            value_label.setWordWrap(True)
            value_label.setAlignment(Qt.AlignmentFlag.AlignTop)
            frame_layout.addWidget(title_label)
            frame_layout.addWidget(value_label)
            self.home_card_labels[key] = value_label
            row = index // 3
            col = index % 3
            card_layout.addWidget(frame, row, col)

        actions = QGroupBox("Quick actions")
        actions.setObjectName("actionGroup")
        actions_layout = QGridLayout(actions)
        actions_layout.setSpacing(12)

        action_specs = [
            ("Refresh status", self.refresh_all),
            ("Open server", self.open_server_web),
            ("Toggle theme", self.toggle_theme),
            ("Scan media", self.scan_media_library),
        ]
        for button_index, (label, callback) in enumerate(action_specs):
            button = QPushButton(label)
            button.setObjectName("accentButton")
            button.clicked.connect(callback)
            actions_layout.addWidget(button, button_index // 2, button_index % 2)

        layout.addWidget(self.home_cards)
        layout.addWidget(actions)
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
        refresh_button = QPushButton("Refresh")
        refresh_button.clicked.connect(lambda: self.load_files(self.current_path or ""))
        rename_button = QPushButton("Rename")
        rename_button.clicked.connect(self.rename_selected_item)
        delete_button = QPushButton("Delete")
        delete_button.clicked.connect(self.delete_selected_item)

        toolbar.addWidget(self.path_input, 1)
        toolbar.addWidget(open_button)
        toolbar.addWidget(upload_button)
        toolbar.addWidget(folder_button)
        toolbar.addWidget(refresh_button)
        toolbar.addWidget(rename_button)
        toolbar.addWidget(delete_button)
        layout.addLayout(toolbar)

        self.file_list = QListWidget()
        self.file_list.itemDoubleClicked.connect(self.open_file_or_directory)
        layout.addWidget(self.file_list, 1)
        return page

    def _build_media_page(self):
        page, layout = self._panel("Media", "Your home media library and playback queue")

        media_toolbar = QHBoxLayout()
        self.media_search = QLineEdit()
        self.media_search.setPlaceholderText("Search media library")
        self.media_search.returnPressed.connect(self.search_media_library)
        self.media_play_button = QPushButton("Play")
        self.media_play_button.clicked.connect(self.play_selected_media)
        self.media_pause_button = QPushButton("Pause")
        self.media_pause_button.clicked.connect(self.media_player.pause)
        self.media_stop_button = QPushButton("Stop")
        self.media_stop_button.clicked.connect(self.media_player.stop)

        media_toolbar.addWidget(self.media_search, 1)
        media_toolbar.addWidget(self.media_play_button)
        media_toolbar.addWidget(self.media_pause_button)
        media_toolbar.addWidget(self.media_stop_button)
        layout.addLayout(media_toolbar)

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
        self.cloud_refresh = QPushButton("Refresh downloads")
        self.cloud_refresh.clicked.connect(lambda: self.load_download_items())

        form_layout.addWidget(QLabel("URL"))
        form_layout.addWidget(self.cloud_url)
        form_layout.addWidget(QLabel("Destination"))
        form_layout.addWidget(self.cloud_destination)
        row = QHBoxLayout()
        row.addWidget(self.cloud_submit)
        row.addWidget(self.cloud_refresh)
        form_layout.addLayout(row)
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
        self.server_url_input.returnPressed.connect(self.connect_to_server)
        connect_button = QPushButton("Connect")
        connect_button.clicked.connect(self.connect_to_server)
        test_button = QPushButton("Test")
        test_button.clicked.connect(self.test_server_connection)

        self.local_server_toggle = QPushButton("Local server: ON")
        self.local_server_toggle.setCheckable(True)
        self.local_server_toggle.setChecked(self.settings.get("start_local_server", True))
        self.local_server_toggle.clicked.connect(self.toggle_local_server)

        self.username_input = QLineEdit()
        self.username_input.setPlaceholderText("Username")
        self.password_input = QLineEdit()
        self.password_input.setPlaceholderText("Password")
        self.password_input.setEchoMode(QLineEdit.Password)
        login_button = QPushButton("Login")
        login_button.clicked.connect(self.login_to_server)

        self.theme_combo = QComboBox()
        self.theme_combo.addItems(["dark", "light"])
        self.theme_combo.setCurrentText(self.settings.get("theme", "dark"))
        self.theme_combo.currentTextChanged.connect(self.apply_theme)

        self.settings_message = QLabel("Ready")
        self.settings_message.setObjectName("inlineMessage")

        row = QHBoxLayout()
        row.addWidget(self.server_url_input, 1)
        row.addWidget(test_button)
        row.addWidget(connect_button)
        settings_layout.addWidget(QLabel("Server URL"))
        settings_layout.addLayout(row)
        settings_layout.addWidget(self.local_server_toggle)

        credentials = QHBoxLayout()
        credentials.addWidget(self.username_input, 1)
        credentials.addWidget(self.password_input, 1)
        settings_layout.addWidget(QLabel("Credentials"))
        settings_layout.addLayout(credentials)
        settings_layout.addWidget(login_button)

        settings_layout.addWidget(QLabel("Theme"))
        settings_layout.addWidget(self.theme_combo)

        action_row = QHBoxLayout()
        clear_auth_button = QPushButton("Clear auth token")
        clear_auth_button.clicked.connect(self.clear_auth_token)
        open_server_button = QPushButton("Open server")
        open_server_button.clicked.connect(self.open_server_web)
        action_row.addWidget(clear_auth_button)
        action_row.addWidget(open_server_button)
        settings_layout.addLayout(action_row)

        settings_layout.addWidget(self.settings_message)
        settings_layout.addStretch()
        layout.addWidget(settings_frame)
        return page

    def apply_theme(self, theme_name):
        theme = (theme_name or "dark").lower()
        dark_styles = """
            QMainWindow {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #090d18, stop:0.4 #101827, stop:1 #0f172a);
                color: #edf2ff;
            }
            QWidget { font-family: 'Segoe UI', sans-serif; }
            #sidebar {
                background: rgba(15, 23, 42, 0.9);
                border: 1px solid rgba(148, 163, 184, 0.18);
                border-radius: 26px;
                box-shadow: 0 14px 30px rgba(15, 23, 42, 0.45);
            }
            #contentPanel {
                background: rgba(15, 23, 42, 0.82);
                border: 1px solid rgba(148, 163, 184, 0.12);
                border-radius: 26px;
                box-shadow: 0 14px 30px rgba(15, 23, 42, 0.35);
            }
            #topBar {
                background: rgba(15, 23, 36, 0.75);
                border: 1px solid rgba(148, 163, 184, 0.12);
                border-radius: 18px;
            }
            #mediaDock {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 rgba(79,70,229,0.24), stop:1 rgba(16,185,129,0.18));
                border: 1px solid rgba(96, 165, 250, 0.24);
                border-radius: 20px;
            }
            #videoPreview {
                background: rgba(2,6,23,0.92);
                border: 1px solid rgba(96,165,250,0.25);
                border-radius: 14px;
            }
            #brand {
                color: #f8fafc;
                font-size: 25px;
                font-weight: 700;
                letter-spacing: 1.8px;
            }
            #sidebarSubtitle {
                color: #9aa9bc;
                font-size: 12px;
                margin-bottom: 8px;
            }
            #navButton {
                background: rgba(148, 163, 184, 0.08);
                color: #eef2ff;
                border: 1px solid rgba(148, 163, 184, 0.12);
                border-radius: 14px;
                padding: 12px 14px;
                text-align: left;
                font-weight: 600;
            }
            #navButton:hover {
                background: rgba(96, 165, 250, 0.18);
                border-color: rgba(96, 165, 250, 0.4);
            }
            #statusBadge {
                color: #7df0b2;
                background: rgba(34, 197, 94, 0.12);
                border: 1px solid rgba(34, 197, 94, 0.25);
                border-radius: 12px;
                padding: 10px 12px;
                font-weight: 600;
            }
            #pageTitle { font-size: 30px; font-weight: 700; color: #f8fafc; }
            #pageSubtitle { color: #9aa9bc; font-size: 13px; }
            #infoCard, #actionGroup {
                background: rgba(15, 23, 36, 0.82);
                border: 1px solid rgba(148, 163, 184, 0.14);
                border-radius: 20px;
            }
            #cardTitle {
                color: #9aa9bc; font-size: 11px; font-weight: 600;
                letter-spacing: 1.3px; text-transform: uppercase;
            }
            #cardValue { color: #f8fafc; font-size: 18px; }
            #nowPlayingTitle { color: #f8fafc; font-size: 16px; font-weight: 600; }
            #mediaMeta { color: #a5b4fc; font-size: 12px; }
            #miniMediaButton {
                background: rgba(15, 23, 42, 0.35);
                border: 1px solid rgba(148,163,184,0.2);
                border-radius: 18px;
                color: #f8fafc;
            }
            #miniMediaButton:hover { background: rgba(96,165,250,0.18); }
            #playbackSlider { background: transparent; }
            #playbackSlider::groove:horizontal {
                border-radius: 8px; height: 8px; background: rgba(148,163,184,0.18);
            }
            #playbackSlider::handle:horizontal {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #8b5cf6, stop:1 #10b981);
                border: none; width: 14px; height: 14px; border-radius: 7px; margin: -3px 0;
            }
            QLabel, QLineEdit, QListWidget, QPushButton, QComboBox { color: #edf2ff; }
            QLineEdit, QListWidget, QComboBox {
                background: rgba(15, 23, 36, 0.9);
                border: 1px solid rgba(148, 163, 184, 0.2);
                border-radius: 12px;
                padding: 10px 12px;
            }
            QPushButton {
                background: rgba(148, 163, 184, 0.08);
                border: 1px solid rgba(148, 163, 184, 0.18);
                border-radius: 12px;
                padding: 10px 14px;
                font-weight: 600;
            }
            QPushButton:hover { background: rgba(96, 165, 250, 0.18); }
            #accentButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 #4f46e5, stop:1 #10b981);
                border: none;
                font-weight: 700;
            }
            QListWidget::item { border-radius: 12px; padding: 10px; }
            QListWidget::item:selected { background: rgba(79, 70, 229, 0.28); }
            #inlineMessage { color: #a5b4fc; }
        """
        light_styles = """
            QMainWindow {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #f3f6fb, stop:1 #e5eef8);
                color: #0f172a;
            }
            QWidget { font-family: 'Segoe UI', sans-serif; }
            #sidebar {
                background: rgba(255, 255, 255, 0.88);
                border: 1px solid rgba(148, 163, 184, 0.18);
                border-radius: 26px;
                box-shadow: 0 12px 28px rgba(148, 163, 184, 0.25);
            }
            #contentPanel {
                background: rgba(255, 255, 255, 0.78);
                border: 1px solid rgba(148, 163, 184, 0.15);
                border-radius: 26px;
                box-shadow: 0 12px 28px rgba(148, 163, 184, 0.2);
            }
            #topBar {
                background: rgba(255, 255, 255, 0.78);
                border: 1px solid rgba(148, 163, 184, 0.12);
                border-radius: 18px;
            }
            #mediaDock {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 rgba(79,70,229,0.12), stop:1 rgba(16,185,129,0.10));
                border: 1px solid rgba(99, 102, 241, 0.18);
                border-radius: 20px;
            }
            #videoPreview {
                background: rgba(15,23,42,0.96);
                border: 1px solid rgba(79,70,229,0.2);
                border-radius: 14px;
            }
            #brand { color: #0f172a; font-size: 25px; font-weight: 700; letter-spacing: 1.8px; }
            #sidebarSubtitle { color: #475569; font-size: 12px; margin-bottom: 8px; }
            #navButton { background: #eef2ff; color: #0f172a; border: 1px solid rgba(99, 102, 241, 0.1); border-radius: 14px; padding: 12px 14px; font-weight: 600; }
            #navButton:hover { background: #e2e8f0; }
            #statusBadge { color: #0c8c5d; background: rgba(34, 197, 94, 0.08); border: 1px solid rgba(34, 197, 94, 0.22); border-radius: 12px; padding: 10px 12px; font-weight: 600; }
            #pageTitle { font-size: 30px; font-weight: 700; color: #0f172a; }
            #pageSubtitle { color: #475569; font-size: 13px; }
            #infoCard, #actionGroup {
                background: rgba(255, 255, 255, 0.9);
                border: 1px solid rgba(148, 163, 184, 0.2);
                border-radius: 20px;
            }
            #cardTitle { color: #475569; font-size: 11px; font-weight: 600; letter-spacing: 1.3px; text-transform: uppercase; }
            #cardValue { color: #0f172a; font-size: 18px; }
            #nowPlayingTitle { color: #0f172a; font-size: 16px; font-weight: 600; }
            #mediaMeta { color: #4f46e5; font-size: 12px; }
            #miniMediaButton { background: rgba(148,163,184,0.18); border: 1px solid rgba(99, 102, 241, 0.1); border-radius: 18px; color: #0f172a; }
            #miniMediaButton:hover { background: rgba(79,70,229,0.16); }
            #playbackSlider { background: transparent; }
            #playbackSlider::groove:horizontal { border-radius: 8px; height: 8px; background: rgba(99,102,241,0.12); }
            #playbackSlider::handle:horizontal { background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #4f46e5, stop:1 #10b981); border: none; width: 14px; height: 14px; border-radius: 7px; margin: -3px 0; }
            QLabel, QLineEdit, QListWidget, QPushButton, QComboBox { color: #0f172a; }
            QLineEdit, QListWidget, QComboBox { background: #f8fafc; border: 1px solid rgba(148, 163, 184, 0.25); border-radius: 12px; padding: 10px 12px; }
            QPushButton { background: #eef2ff; border: 1px solid rgba(99, 102, 241, 0.12); border-radius: 12px; padding: 10px 14px; font-weight: 600; }
            QPushButton:hover { background: #e2e8f0; }
            #accentButton { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #4f46e5, stop:1 #10b981); border: none; color: white; font-weight: 700; }
            QListWidget::item { border-radius: 12px; padding: 10px; }
            QListWidget::item:selected { background: rgba(79, 70, 229, 0.18); }
            #inlineMessage { color: #4f46e5; }
        """
        self.setStyleSheet(dark_styles if theme == "dark" else light_styles)
        self.settings["theme"] = theme
        if hasattr(self, "theme_combo") and self.theme_combo.currentText() != theme:
            self.theme_combo.setCurrentText(theme)
        SettingsStore.save(self.settings)

    def refresh_all(self):
        self.refresh_server_state()
        if self.nav.currentIndex() == self.nav_index["files"]:
            self.load_files(self.current_path or "")
        if self.nav.currentIndex() == self.nav_index["media"]:
            self.load_media_catalog()
        if self.nav.currentIndex() == self.nav_index["downloads"]:
            self.load_download_items()

    def toggle_theme(self):
        new_theme = "light" if self.settings.get("theme", "dark") == "dark" else "dark"
        self.apply_theme(new_theme)

    def scan_media_library(self):
        try:
            response = self.api.post("/api/media/scan")
            if response.ok:
                self.settings_message.setText("Media scan started")
                self.load_media_catalog()
            else:
                self.settings_message.setText(f"Scan failed: {response.text}")
        except Exception as exc:
            self.settings_message.setText(f"Scan error: {exc}")

    def open_server_web(self):
        try:
            import webbrowser
            webbrowser.open(self.settings.get("server_url", f"http://127.0.0.1:{DEFAULT_PORT}"))
        except Exception:
            QMessageBox.information(self, "Open server", "Open the server URL manually in your browser.")

    def quick_search(self):
        query = self.search_input.text().strip()
        if not query:
            self.refresh_all()
            return
        current_index = self.nav.currentIndex()
        if current_index == self.nav_index["files"]:
            self.load_files(self.current_path or "")
            self.file_list.clear()
            self.file_list.addItem(f"Search for '{query}' is available through the server API.")
        elif current_index == self.nav_index["media"]:
            self.search_media_library(query)
        else:
            self.settings_message.setText(f"Search: {query}")

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
                    self.load_files(self.current_path or "")
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

    def set_server_url(self, url, *, clear_auth=True):
        normalized = url.strip().rstrip("/")
        if not normalized:
            return False
        self.settings["server_url"] = normalized
        SettingsStore.save(self.settings)
        self.api = ApiClient(normalized)
        if clear_auth:
            self.api.token = None
            AuthManager().clear()
        self.server_url_input.setText(normalized)
        self.settings_message.setText("Server changed. Login again to this server." if clear_auth else "Server updated successfully.")
        self.refresh_server_state()
        return True

    def connect_to_server(self):
        url = self.server_url_input.text().strip()
        if not url:
            self.settings_message.setText("Enter a server URL first")
            return
        self.set_server_url(url, clear_auth=True)

    def test_server_connection(self):
        url = self.server_url_input.text().strip().rstrip("/")
        if not url:
            self.settings_message.setText("Enter a server URL first")
            return
        try:
            response = requests.get(f"{url}/api/health", timeout=5)
            response.raise_for_status()
            payload = response.json()
            self.settings_message.setText(f"Online: {payload.get('server', 'DhilipHome Server')} v{payload.get('version', 'unknown')}")
        except Exception as exc:
            self.settings_message.setText(f"Connection failed: {exc}")

    def toggle_local_server(self, enabled):
        self.settings["start_local_server"] = bool(enabled)
        self.local_server_toggle.setText("Local server: ON" if enabled else "Local server: OFF")
        SettingsStore.save(self.settings)
        self.settings_message.setText("Restart the app to apply local server startup setting.")

    def login_to_server(self):
        username = self.username_input.text().strip()
        password = self.password_input.text().strip()
        if not username or not password:
            self.settings_message.setText("Enter username and password")
            return
        ok, message = self.api.login(username, password)
        if ok:
            self.settings_message.setText("Authentication successful")
            self.refresh_server_state()
        else:
            self.settings_message.setText(f"Login failed: {message}")

    def clear_auth_token(self):
        AuthManager().clear()
        self.api.token = None
        self.settings_message.setText("Authentication cleared")

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
                item.setData(Qt.UserRole, folder)
                self.file_list.addItem(item)
            for file in payload.get("files", []):
                item = QListWidgetItem(f"File: {file['name']}")
                item.setData(Qt.UserRole, file)
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
        mime_type = payload.get("mime_type") or mimetypes.guess_type(payload.get("name", ""))[0] or ""
        if str(mime_type).startswith("video/"):
            self.open_media_item(item)
        else:
            self.download_file(payload)

    def download_file(self, payload):
        path = payload.get("path")
        if not path:
            return
        try:
            response = self.api.get("/api/files/download", params={"path": path}, stream=True)
            response.raise_for_status()
            destination, _ = QFileDialog.getSaveFileName(self, "Save file", payload.get("name", Path(path).name))
            if not destination:
                return
            with open(destination, "wb") as handle:
                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if chunk:
                        handle.write(chunk)
            self.settings_message.setText(f"Saved {Path(destination).name}")
        except Exception as exc:
            QMessageBox.warning(self, "Download", str(exc))

    def load_media_catalog(self):
        try:
            response = self.api.get("/api/media")
            if not response.ok:
                self.media_list.clear()
                self.media_list.addItem("Media catalog unavailable")
                return
            payload = response.json().get("data", response.json())
            self.media_list.clear()
            items = payload.get("items", []) if isinstance(payload, dict) else payload
            for item in items:
                row = QListWidgetItem(f"{item.get('filename', 'Unknown')} ({item.get('category', 'Other')})")
                row.setData(Qt.UserRole, item)
                self.media_list.addItem(row)
        except Exception:
            self.media_list.clear()
            self.media_list.addItem("Unable to load media catalog")

    def search_media_library(self, query=None):
        query = (query or self.media_search.text()).strip()
        if not query:
            self.load_media_catalog()
            return
        try:
            response = self.api.get("/api/media/search", params={"q": query})
            if not response.ok:
                self.media_list.clear()
                self.media_list.addItem("No media matches were found")
                return
            payload = response.json().get("data", response.json())
            self.media_list.clear()
            for item in payload.get("items", []):
                row = QListWidgetItem(f"{item.get('filename', 'Unknown')} ({item.get('category', 'Other')})")
                row.setData(Qt.UserRole, item)
                self.media_list.addItem(row)
        except Exception:
            self.media_list.clear()
            self.media_list.addItem("Unable to search media catalog")

    def play_selected_media(self):
        selected_item = self.media_list.currentItem()
        if selected_item is not None:
            self.open_media_item(selected_item)
        else:
            QMessageBox.information(self, "Media", "Select a media item to play.")

    def _animate_media_dock(self, visible):
        if not hasattr(self, "media_dock"):
            return
        self.media_dock.setVisible(True)
        self.media_dock.raise_()
        opacity = self.media_dock.graphicsEffect()
        if opacity is None:
            opacity = QGraphicsOpacityEffect(self.media_dock)
            self.media_dock.setGraphicsEffect(opacity)
        opacity.setOpacity(1.0 if visible else 0.0)
        animation = QPropertyAnimation(self.media_dock, b"maximumHeight")
        animation.setDuration(260)
        animation.setEasingCurve(QEasingCurve.Type.OutCubic)
        animation.setStartValue(self.media_dock.maximumHeight())
        animation.setEndValue(180 if visible else 0)
        animation.start()
        if not visible:
            QTimer.singleShot(270, self.media_dock.hide)

    def seek_media(self, value):
        if self.media_player.isSeekable():
            duration = self.media_player.duration()
            if duration > 0:
                self.media_player.setPosition(int((value / 100) * duration))

    def open_media_item(self, item):
        payload = item.data(Qt.UserRole)
        if not payload:
            return

        media_path = payload.get("path") or payload.get("relative_path") or payload.get("filename")
        if not media_path:
            return

        mime_type = payload.get("mime_type") or mimetypes.guess_type(payload.get("filename", payload.get("name", "")))[0] or ""
        if not str(mime_type).startswith("video/"):
            self.download_file(payload)
            return

        encoded_path = urllib.parse.quote(str(media_path).lstrip("/"), safe="/")
        stream_url = self.settings["server_url"].rstrip("/") + "/api/media/stream/" + encoded_path
        if self.api.token:
            stream_url = f"{stream_url}?token={urllib.parse.quote(self.api.token, safe='')}"

        self.now_playing_label.setText(payload.get("filename", payload.get("name", "Media")))
        self.media_meta_label.setText("Opening in the Windows default video player")
        if not QDesktopServices.openUrl(QUrl(stream_url)):
            webbrowser.open(stream_url)
        self.status_badge.setText(f"Opened {payload.get('filename', payload.get('name', 'video'))}")

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
        destination = self.cloud_destination.text().strip() or self.current_path or ""
        if not url:
            QMessageBox.warning(self, "Cloud download", "Add a valid URL first")
            return
        try:
            filename = Path(url.split("?", 1)[0].rstrip("/")).name or "download.bin"
            response = self.api.post("/api/files/remote-download", json={"url": url, "filename": filename, "destination": destination})
            if not response.ok:
                self._show_api_error("Cloud download", response)
                return
            task = response.json().get("data", {})
            self.cloud_url.clear()
            self.cloud_destination.clear()
            self.load_download_items()
            self.settings_message.setText(f"Download queued: {task.get('filename', filename)}")
        except Exception as exc:
            QMessageBox.warning(self, "Cloud download", str(exc))

    def _show_api_error(self, title, response):
        try:
            payload = response.json()
            error = payload.get("error", {})
            message = error.get("message") or payload.get("message") or response.text
        except ValueError:
            message = response.text
        QMessageBox.warning(self, title, f"Server returned HTTP {response.status_code}: {message}")

    def rename_selected_item(self):
        current = self.file_list.currentItem()
        if current is None:
            QMessageBox.information(self, "Rename", "Select a file or folder to rename.")
            return
        payload = current.data(Qt.UserRole) or {}
        path = payload.get("path")
        if not path:
            return
        new_name, ok = QInputDialog.getText(self, "Rename", "New name")
        if not ok or not new_name.strip():
            return
        try:
            response = self.api.post("/api/files/rename", json={"path": path, "new_name": new_name.strip()})
            if not response.ok:
                QMessageBox.warning(self, "Rename", response.text)
                return
            self.load_files(self.current_path)
        except Exception as exc:
            QMessageBox.warning(self, "Rename", str(exc))

    def delete_selected_item(self):
        current = self.file_list.currentItem()
        if current is None:
            QMessageBox.information(self, "Delete", "Select an item to delete.")
            return
        payload = current.data(Qt.UserRole) or {}
        path = payload.get("path")
        if not path:
            return
        confirm = QMessageBox.question(self, "Delete item", f"Remove '{path}' from the server?", QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
        if confirm != QMessageBox.StandardButton.Yes:
            return
        try:
            response = self.api.delete("/api/files", params={"path": path})
            if not response.ok:
                QMessageBox.warning(self, "Delete", response.text)
                return
            self.load_files(self.current_path)
        except Exception as exc:
            QMessageBox.warning(self, "Delete", str(exc))

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
                    timeout=(15, 3600),
                )
            if not response.ok:
                self._show_api_error("Upload", response)
                return
            uploaded = response.json().get("data", {})
            self.settings_message.setText(f"Uploaded {uploaded.get('name', Path(file_path).name)}")
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

    def _apply_brand_splash(self):
        self.setWindowTitle(APP_NAME)
        self.setWindowIcon(make_app_icon(128))


if __name__ == "__main__":
    startup_settings = SettingsStore.load()
    if startup_settings.get("start_local_server", True):
        threading.Thread(target=start_server, daemon=True).start()
    app = QApplication(sys.argv)
    app.setWindowIcon(make_app_icon(128))
    app.setApplicationName(APP_NAME)
    app.setApplicationDisplayName(APP_NAME)
    window = DhilipHomeWindow()
    window.show()
    sys.exit(app.exec())
