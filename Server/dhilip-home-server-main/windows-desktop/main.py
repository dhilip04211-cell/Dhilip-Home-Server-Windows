import sys, os, json, threading, time, mimetypes
from pathlib import Path
import requests

from PySide6.QtCore import Qt, QUrl, QTimer
from PySide6.QtGui import QAction
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QListWidget, QListWidgetItem, QStackedWidget, QFrame,
    QLineEdit, QMessageBox, QFileDialog, QProgressBar, QInputDialog
)
from PySide6.QtMultimedia import QMediaPlayer, QAudioOutput
from PySide6.QtMultimediaWidgets import QVideoWidget

APP_NAME="Dhilip Home"
PORT=8080

# Start the existing server in-process. This keeps the Android API contract intact.
_server_error=None
def start_server():
    global _server_error
    try:
        from app import create_app, socketio
        from app.utils.config import Config
        flask_app=create_app()
        socketio.run(flask_app, host=Config.HOST, port=Config.PORT,
                     debug=False, allow_unsafe_werkzeug=True)
    except Exception as e:
        _server_error=e

class Api:
    def __init__(self, base):
        self.base=base.rstrip("/")
        self.token=None
        self.s=requests.Session()
    def headers(self):
        return {"Authorization":"Bearer "+self.token} if self.token else {}
    def get(self,path,**kw):
        return self.s.get(self.base+path,headers=self.headers(),timeout=15,**kw)
    def post(self,path,**kw):
        return self.s.post(self.base+path,headers=self.headers(),timeout=30,**kw)
    def delete(self,path,**kw):
        return self.s.delete(self.base+path,headers=self.headers(),timeout=15,**kw)

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(APP_NAME)
        self.resize(1280,800)
        self.api=Api(f"http://127.0.0.1:{PORT}")
        self.current_path=""
        self.player=QMediaPlayer(self)
        self.audio=QAudioOutput(self)
        self.player.setAudioOutput(self.audio)
        self.video=QVideoWidget()
        self.player.setVideoOutput(self.video)

        central=QWidget(); self.setCentralWidget(central)
        root=QHBoxLayout(central); root.setContentsMargins(0,0,0,0); root.setSpacing(0)
        side=QFrame(); side.setObjectName("sidebar"); side.setFixedWidth(235)
        sl=QVBoxLayout(side); sl.setContentsMargins(16,22,16,16)
        brand=QLabel("DHILIP HOME"); brand.setObjectName("brand"); sl.addWidget(brand)
        self.nav=QStackedWidget(); root.addWidget(side); root.addWidget(self.nav,1)
        for label, page in [
            ("⌂  Home",self.home()),
            ("▣  Files",self.files()),
            ("▶  Media",self.media()),
            ("⇩  Downloads",self.downloads()),
            ("☁  Cloud",self.cloud()),
            ("⚙  Settings",self.settings())]:
            b=QPushButton(label); b.setObjectName("nav")
            idx=self.nav.addWidget(page); b.clicked.connect(lambda _,i=idx:self.nav.setCurrentIndex(i))
            sl.addWidget(b)
        sl.addStretch()
        self.status=QLabel("●  Starting server…"); self.status.setObjectName("status"); sl.addWidget(self.status)

        self.timer=QTimer(self); self.timer.timeout.connect(self.refresh); self.timer.start(3000)
        QTimer.singleShot(1800,self.refresh)

    def page(self,title,sub):
        w=QWidget(); l=QVBoxLayout(w); l.setContentsMargins(32,28,32,28)
        t=QLabel(title); t.setObjectName("pageTitle"); l.addWidget(t)
        s=QLabel(sub); s.setObjectName("subtitle"); l.addWidget(s); l.addSpacing(16)
        return w,l

    def home(self):
        w,l=self.page("Home","Everything for your personal server in one place")
        self.home_info=QLabel("Connecting to Dhilip Home Server…"); self.home_info.setObjectName("card")
        l.addWidget(self.home_info); l.addStretch(); return w

    def files(self):
        w,l=self.page("Files","Browse, play, upload and manage server storage")
        bar=QHBoxLayout()
        self.path_edit=QLineEdit(); self.path_edit.setPlaceholderText("Current folder")
        go=QPushButton("Open"); go.clicked.connect(lambda:self.load_files(self.path_edit.text()))
        up=QPushButton("Upload"); up.clicked.connect(self.upload)
        new=QPushButton("New Folder"); new.clicked.connect(self.new_folder)
        bar.addWidget(self.path_edit,1); bar.addWidget(go); bar.addWidget(up); bar.addWidget(new)
        l.addLayout(bar)
        self.file_list=QListWidget(); self.file_list.itemDoubleClicked.connect(self.file_open)
        l.addWidget(self.file_list,1)
        return w

    def media(self):
        w,l=self.page("Media","Your media library and built-in player")
        self.media_list=QListWidget(); self.media_list.itemDoubleClicked.connect(self.media_open)
        l.addWidget(self.media_list,1)
        return w

    def downloads(self):
        w,l=self.page("Downloads","Server-side cloud download state")
        self.download_list=QListWidget(); l.addWidget(self.download_list,1)
        return w

    def cloud(self):
        w,l=self.page("Cloud Downloads","Downloads are performed directly by your Home Server")
        row=QHBoxLayout()
        self.url=QLineEdit(); self.url.setPlaceholderText("https://example.com/file")
        self.dest=QLineEdit(); self.dest.setPlaceholderText("Destination folder, e.g. Downloads")
        start=QPushButton("Start Download"); start.clicked.connect(self.start_download)
        row.addWidget(self.url,2); row.addWidget(self.dest,1); row.addWidget(start)
        l.addLayout(row)
        l.addStretch(); return w

    def settings(self):
        w,l=self.page("Settings","Server connection and application information")
        self.settings_info=QLabel(f"Server: http://127.0.0.1:{PORT}\\nLAN access uses the same server API.\\nAndroid and Windows share server-side state.")
        self.settings_info.setObjectName("card"); l.addWidget(self.settings_info)
        l.addStretch(); return w

    def refresh(self):
        try:
            r=self.api.get("/api/health")
            if r.ok:
                self.status.setText("●  Server Online")
                self.status.setStyleSheet("color:#72e0a2;padding:12px;")
                if hasattr(self,"home_info"):
                    try:
                        sysinfo=self.api.get("/api/system").json()
                        self.home_info.setText("Server ONLINE\\n\\nHealth: OK\\nPort: 8080\\n\\n" + json.dumps(sysinfo,indent=2)[:1600])
                    except: self.home_info.setText("Server ONLINE\\n\\nHealth: OK\\nPort: 8080")
                if hasattr(self,"file_list") and self.nav.currentIndex()==1: self.load_files(self.current_path)
                if hasattr(self,"media_list") and self.nav.currentIndex()==2: self.load_media()
                if hasattr(self,"download_list") and self.nav.currentIndex()==3: self.load_downloads()
            else: self.status.setText("●  Server Error")
        except Exception:
            self.status.setText("●  Connecting…")

    def load_files(self,path=""):
        self.current_path=path.strip("/")
        self.path_edit.setText(self.current_path)
        try:
            data=self.api.get("/api/files/list",params={"path":self.current_path}).json()
            self.file_list.clear()
            items=data.get("data",data).get("items",[]) if isinstance(data,dict) else []
            for x in items:
                name=x.get("name",str(x)); isdir=x.get("is_directory",x.get("type")=="directory")
                item=QListWidgetItem(("📁 " if isdir else "📄 ")+name)
                item.setData(Qt.UserRole,{"name":name,"isdir":isdir,"path":((self.current_path+"/") if self.current_path else "")+name})
                self.file_list.addItem(item)
        except Exception as e: self.file_list.clear(); self.file_list.addItem("Unable to load files: "+str(e))

    def file_open(self,item):
        d=item.data(Qt.UserRole) or {}; p=d.get("path","")
        if d.get("isdir"): self.load_files(p); return
        if mimetypes.guess_type(d.get("name",""))[0] or d.get("name","").lower().endswith((".mp4",".mkv",".webm",".mp3",".m4a",".wav")):
            self.play_path(p)

    def load_media(self):
        try:
            data=self.api.get("/api/media").json()
            obj=data.get("data",data) if isinstance(data,dict) else {}
            items=obj.get("items",[]) if isinstance(obj,dict) else []
            self.media_list.clear()
            for x in items:
                p=x.get("path",x.get("relative_path",x.get("name","")))
                it=QListWidgetItem("▶  "+x.get("name",Path(p).name))
                it.setData(Qt.UserRole,p); self.media_list.addItem(it)
        except Exception as e: self.media_list.clear(); self.media_list.addItem("Unable to load media: "+str(e))

    def media_open(self,item):
        self.play_path(item.data(Qt.UserRole))

    def play_path(self,path):
        if not path: return
        url=f"http://127.0.0.1:{PORT}/api/media/stream/{QUrl.toPercentEncoding(path).data().decode()}"
        if self.api.token: url += ("&" if "?" in url else "?")+"token="+self.api.token
        self.nav.addWidget(self.player_page(path,url))
        self.nav.setCurrentIndex(self.nav.count()-1)
        self.player.setSource(QUrl(url)); self.player.play()

    def player_page(self,title,url):
        w=QWidget(); l=QVBoxLayout(w); back=QPushButton("← Back"); back.clicked.connect(lambda:self.nav.setCurrentIndex(2))
        l.addWidget(back); h=QLabel(title); h.setObjectName("pageTitle"); l.addWidget(h); l.addWidget(self.video,1)
        return w

    def load_downloads(self):
        try:
            data=self.api.get("/api/files/remote-download").json()
            obj=data.get("data",data); items=obj.get("items",[]) if isinstance(obj,dict) else []
            self.download_list.clear()
            for x in items:
                self.download_list.addItem(f"{x.get('filename','download')}  |  {x.get('status','')}  |  {x.get('progress_percent',0)}%")
        except Exception as e: self.download_list.clear(); self.download_list.addItem(str(e))

    def start_download(self):
        url=self.url.text().strip()
        if not url: return
        try:
            r=self.api.post("/api/files/remote-download",json={"url":url,"destination":self.dest.text().strip()})
            if not r.ok: QMessageBox.warning(self,"Download",r.text)
            self.load_downloads()
        except Exception as e: QMessageBox.warning(self,"Download",str(e))

    def upload(self):
        fn,_=QFileDialog.getOpenFileName(self,"Select file")
        if not fn: return
        try:
            with open(fn,"rb") as f:
                r=self.api.post("/api/files/upload",files={"file":(Path(fn).name,f)},data={"path":self.current_path})
            if not r.ok: QMessageBox.warning(self,"Upload",r.text)
            self.load_files(self.current_path)
        except Exception as e: QMessageBox.warning(self,"Upload",str(e))

    def new_folder(self):
        name,ok=QInputDialog.getText(self,"New Folder","Folder name:")
        if ok and name:
            r=self.api.post("/api/files/folder",json={"path":((self.current_path+"/") if self.current_path else "")+name})
            if not r.ok: QMessageBox.warning(self,"Folder",r.text)
            self.load_files(self.current_path)

app=QApplication(sys.argv)
app.setStyle("Fusion")
app.setStyleSheet("""
QMainWindow{background:#0f1117;color:#f5f7fb}
#sidebar{background:#151922;border-right:1px solid #282d38}
#brand{font-size:22px;font-weight:800;padding:10px 8px 20px}
#nav{border:0;border-radius:10px;text-align:left;padding:14px;color:#cbd1dc;font-size:15px}
#nav:hover{background:#242a35}
#status{padding:12px;color:#72e0a2}
#pageTitle{font-size:28px;font-weight:750}
#subtitle{color:#9299a8}
#card{background:#191e27;border:1px solid #2a303c;border-radius:16px;padding:22px;font-size:15px}
QPushButton{background:#202632;border:1px solid #323946;border-radius:9px;padding:9px 14px}
QPushButton:hover{background:#2a3140}
QLineEdit,QListWidget{background:#171c24;border:1px solid #303744;border-radius:10px;padding:8px;color:#f5f7fb}
QListWidget::item{padding:12px;border-radius:8px}
QListWidget::item:selected{background:#263246}
""")
threading.Thread(target=start_server,daemon=True).start()
win=MainWindow(); win.show()
sys.exit(app.exec())
