# DhilipHome 0.3.3 — Storage and Transfer Fix

## What was fixed

- Windows runtime database/log/config storage no longer lives under `C:\Program Files\Dhilip Home`.
- Windows media storage defaults to the user Documents folder and is writable by the desktop app.
- Existing `download_jobs` SQLite databases are migrated with missing columns added automatically.
- Cloud-download job creation reports database/storage errors instead of an unexplained HTTP 500.
- Uploads use a temporary `.uploading` file and atomic replacement, preventing partial files from appearing as completed uploads.
- Upload storage permission errors are reported explicitly.
- Default upload limit is 10 GB and remains configurable with `MAX_CONTENT_LENGTH_MB`.
- Stale HTTP Range downloads receiving `416` are restarted safely from zero.
- Android file-upload network timeouts were increased for large LAN uploads.
- Android cloud-download command/status calls continue using short dedicated timeouts, so they do not wait for a large file transfer.

## Windows installation

Install the new `DhilipHome-Setup-0.3.3.exe` and restart DhilipHome. Runtime data is stored under the current Windows user profile rather than Program Files.

If an older installation contains files under its old `Server\...\media` directory, copy/move those files to the new configured media directory before using them. The database schema migration is automatic when the old database is used through `DATABASE_PATH`.

## Environment overrides

- `DHILIPHOME_DATA_DIR` — writable root for database/log/config.
- `MEDIA_ROOT` — authoritative media/file root.
- `DATABASE_PATH` — authoritative SQLite path.
- `LOG_PATH` — server log path.
- `CONFIG_DIR` — config directory.
- `MAX_CONTENT_LENGTH_MB` — upload request limit.
