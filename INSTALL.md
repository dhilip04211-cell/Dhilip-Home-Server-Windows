# Installer and installation notes

1. Download the generated `DhilipHome-Setup.exe` from the release workflow or your build pipeline.
2. Run the installer with normal user privileges.
3. Accept the default install directory or choose a custom path.
4. Keep the local server and media root on the same machine as the app unless you intentionally configure an alternate server URL.

## Important

- Do not move the media root or database away from the configured server directories without updating your server configuration.
- The app is meant to connect to the DhilipHome server over LAN or localhost.
- The server remains authoritative for files, downloads, and media state.


### 0.3.3 storage fix

Windows 0.3.3 stores mutable runtime data outside Program Files so uploads and server-managed cloud downloads can write successfully. See `Server/dhilip-home-server-main/UPGRADE_0.3.3.md`.
