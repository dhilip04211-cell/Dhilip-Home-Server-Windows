# DhilipHome 0.3.1 — Direct Server Download Reliability

- Cloud download requests return immediately with HTTP 202 and a persistent task ID.
- The Debian Home Server performs the actual Internet download directly.
- No Cloudflare or environment HTTP proxy is used by the downloader.
- Download jobs are persisted in SQLite and survive Android cache clearing.
- Real speed, bytes, percentage and ETA are stored on the server.
- Pause/resume/cancel endpoints are server-side.
- Resume uses HTTP Range when the remote host supports it.
- Partial `.part` files are preserved for pause/retry and removed on cancel.
- Completed downloads are atomically renamed into MEDIA_ROOT.
- Existing Android client now fails fast on command/status requests and explicitly
  sends the authentication token, avoiding an indefinite "Sending request..." UI.
- Android rehydrates download state from the server on startup.
- Android exposes pause/resume/cancel controls against server state.
