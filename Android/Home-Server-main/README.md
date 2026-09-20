# DhilipHome Android 0.3.3

Production-oriented Android/Android TV client for the DhilipHome private server.

## Highlights

- Server-authoritative cloud downloads with real speed/progress and cancellation
- Media3/ExoPlayer streaming with HTTP Range support
- Glass-style transparent player controls
- Files list/grid modes
- Upload and create-folder actions
- Admin-only rename/delete controls
- Android TV D-pad compatible navigation

## Build

Open the `Android` directory in Android Studio with a current Android SDK. The project targets SDK 36 and uses Kotlin/Compose.

Set the required `.env` values from `.env.example` before a release build. Do not commit production secrets or keystores.
