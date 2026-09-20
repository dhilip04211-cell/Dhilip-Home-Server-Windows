# DhilipHome Server 0.3.3

Private LAN home media/file server for Debian/Ubuntu. The server is the source of truth for files, media indexing and cloud-download jobs.

## Production setup

1. Create and activate a Python virtual environment.
2. Install `requirements.txt`.
3. Copy `.env.example` to `.env`.
4. Set a long random `DHILIPHOME_SECRET_KEY`, admin credentials, and storage paths.
5. Start with the supplied systemd unit.

Example:

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python server.py
```

## Important endpoints

- `/api/health` — health
- `/api/auth/login` — login
- `/api/files` — list/delete (delete is admin-only)
- `/api/files/rename` — admin-only rename
- `/api/files/folder` — create folder
- `/api/files/upload` — upload
- `/api/files/remote-download` — server-side cloud download + status
- `/api/files/remote-download/cancel` — cancel server-side cloud download
- `/api/media` — indexed media
- `/api/media/stream/<path>` — authenticated HTTP Range streaming

Partial `.part` downloads are never exposed as completed media and are removed after cancellation/failure.
