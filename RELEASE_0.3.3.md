# DhilipHome 0.3.3 — Unified Transfer Fix

## Fixed
- Windows runtime database/log/config storage is outside Program Files.
- Existing `download_jobs` databases are migrated automatically.
- Server-side cloud downloads remain authoritative and resumable.
- Windows uploads use a long transfer timeout.
- Uploads are written to a temporary file and atomically moved into place.
- Stale HTTP Range 416 responses restart a partial cloud download safely.
- Android cloud-download command/status requests remain short and independent of file-transfer duration.
- Android upload logs now preserve the server's HTTP error body for diagnosis.
- Android version is 0.3.3 / versionCode 4.
- Windows server/installer version is 0.3.3.
