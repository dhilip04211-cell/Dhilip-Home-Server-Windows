# Architecture

## Topology

```text
Android / Android TV / Windows client
      \                     /
       \__ same API _______/
                |
                v
        DhilipHome Server
                |
                v
        SQLite database
                |
                v
        Media and download storage
```

## Responsibilities

- Android and Windows clients: UI and local user interactions
- Server: source of truth for files, authentication, media indexing, downloads, and discovery
- Database: shared state for the app ecosystem
- Media root: file system used for uploads, downloads, and streaming

## Why this approach

It keeps the original Android app fully compatible and prevents the Windows build from diverging into a fake local-only state store.
