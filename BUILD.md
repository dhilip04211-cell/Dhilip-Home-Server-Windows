# Build Guide

## Python backend

```bash
cd Server/dhilip-home-server-main
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python server.py
```

## Desktop packaging

On Windows:

```powershell
cd Server\dhilip-home-server-main
python -m pip install -r windows-desktop\requirements-windows.txt
python -m pip install pyinstaller
pyinstaller --clean --noconfirm windows-desktop\DhilipHome.spec
```

The build output will generate the main executable in the `dist` folder, and the installer workflow packages the release artifact for distribution.
