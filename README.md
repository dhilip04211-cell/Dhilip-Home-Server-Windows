# Dhilip Home

This workspace contains the Dhilip Home server and the Windows desktop shell that packages it into a single application experience.

## Components

- Server source: `Server/dhilip-home-server-main`
- Windows desktop app: `Server/dhilip-home-server-main/windows-desktop/main.py`
- GitHub Actions: `.github/workflows/build-modern-windows-installer.yml`

## Key design principle

The server remains the single source of truth. The Windows UI reads and writes through the same Flask API used by the Android app, so a user can move from Android to Windows without a parallel data store.

## Quick start

```bash
cd Server/dhilip-home-server-main
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python server.py
```

## Windows desktop app

```bash
cd Server/dhilip-home-server-main
python -m pip install -r windows-desktop/requirements-windows.txt
python windows-desktop/main.py
```

## Notes

- The Windows app starts the same backend server in-process.
- It keeps the original Android compatibility contract intact.
- Do not use separate Windows-only downloads or duplicate storage state.
