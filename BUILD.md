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

## Android release signing

The Android release build uses `Android/Home-Server-main/my-upload-key.jks` by default. Generate it once from the repository root with:

```bash
keytool -genkeypair -v -keystore Android/Home-Server-main/my-upload-key.jks \
	-alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and its passwords private. Set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` before running `./gradlew assembleRelease`.
