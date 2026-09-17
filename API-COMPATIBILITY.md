# API compatibility map

The Windows client uses the existing backend endpoints rather than inventing a separate API.

## Public endpoints

- `GET /api/health` — health check
- `GET /api/server` — server identity and network details
- `GET /api/discovery` — LAN discovery payload
- `GET /api/system` — system metrics
- `GET /api/network` — network interfaces

## Authentication

- `POST /api/auth/login`
- `GET /api/auth/status`
- `POST /api/auth/logout`

## Files and downloads

- `GET /api/files/list` or `GET /api/files`
- `POST /api/files/upload`
- `POST /api/files/folder`
- `POST /api/files/rename` — admin-only
- `DELETE /api/files` — admin-only
- `POST /api/files/remote-download`
- `GET /api/files/remote-download`
- `POST /api/files/remote-download/pause`
- `POST /api/files/remote-download/resume`
- `POST /api/files/remote-download/cancel`

## Media

- `GET /api/media`
- `GET /api/media/search`
- `POST /api/media/scan`
- `GET /api/media/stream/<path>`

## Compatibility notes

The Android app and Windows app both rely on the same backend and database. The Windows shell is a client, not a second server.
