# Standalone Windows GUI EXE. No Python installation is required on the target PC.
from pathlib import Path

from PyInstaller.utils.hooks import collect_submodules

project_root = Path.cwd()
hiddenimports = collect_submodules('app') + [
    'engineio.async_drivers.threading', 'simple_websocket'
]

a = Analysis(
    [str(project_root / 'dhiliphome_gui.py')],
    pathex=[str(project_root)],
    binaries=[],
    datas=[],
    hiddenimports=hiddenimports,
    hookspath=[], hooksconfig={}, runtime_hooks=[], excludes=[], noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz, a.scripts, a.binaries, a.datas, [],
    name='DhilipHomeServer', debug=False, bootloader_ignore_signals=False,
    strip=False, upx=True, console=False,
)
