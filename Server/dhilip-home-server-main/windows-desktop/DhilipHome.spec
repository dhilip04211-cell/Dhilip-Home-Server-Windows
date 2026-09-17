# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
from PyInstaller.utils.hooks import collect_submodules

project_root=Path.cwd()
desktop=project_root/"windows-desktop"

hiddenimports=collect_submodules("app")+[
    "engineio.async_drivers.threading",
    "engineio.async_drivers",
    "PySide6.QtMultimedia",
    "PySide6.QtMultimediaWidgets",
]

a=Analysis(
    [str(desktop/"main.py")],
    pathex=[str(project_root),str(desktop)],
    binaries=[],
    datas=[],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz=PYZ(a.pure)
exe=EXE(
    pyz,a.scripts,a.binaries,a.datas,[],
    name="DhilipHome",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
)
