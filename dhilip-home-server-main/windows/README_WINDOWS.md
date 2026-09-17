# DhilipHome Server — Windows EXE

This package adds a native Windows build path for the existing DhilipHome Server 0.3.1 API.

## Build the EXE

On a Windows PC with Python 3.11 installed:

1. Open this folder.
2. Double-click `windows\build_windows.bat`.
3. The finished executable will be:
   `dist\DhilipHomeServer.exe`

The EXE contains the Flask/API/WebSocket server and keeps persistent server data beside the executable:

- `media\` — server media/files
- `data\dhiliphome.db` — database/state
- `logs\server.log` — server logs
- `.env` — server configuration

## Android compatibility

The Android APK should continue using the same API endpoints and server-side cloud-download flow. The Windows EXE is only another host for the same server.

Default:
- HTTP: `8080`
- UDP discovery: `8888`
- Bind: `0.0.0.0`

After starting, the console displays the LAN URL. Example:
`http://192.168.x.x:8080`

If Windows Firewall asks for network access, allow the application on the **Private network**.

## Important

Do not put the EXE in a protected folder such as `C:\Program Files` if you want the server to write its database, logs and media beside it. A folder such as `C:\DhilipHomeServer` is recommended.

For an actual Windows `.exe`, the build must run on Windows (or a Windows CI runner). The supplied GitHub Actions workflow can build it automatically.
