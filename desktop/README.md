# Nuvora Desktop

A separate Tauri 2 + React + TypeScript desktop client under `desktop/`. The Android app remains in the repository root and is not migrated or replaced.

## Implemented in this foundation
- Dark navy/cyan responsive interface with Home, Search, Discover, Library and Settings.
- Single HTML audio playback service for compatible direct streams: Radio Paradise MP3, SomaFM Groove Salad and Drone Zone.
- Audius public API search with debounce, abortable requests and malformed-response validation. Results are honestly marked metadata-only until an authorized stream resolver is implemented.
- Official-service handoff for YouTube Music, Spotify and JioSaavn.
- Local favourites, volume and seek controls, and explicit playback errors.

## Not implemented yet
Authorized Audius stream resolution, playlist management, queue/reorder, artwork cache management, gapless playback, equalizer, sleep timer, system media keys and verified real-hardware performance measurements. These are not represented as completed features.

## Requirements and setup
Install Node.js 20+, npm, Rust stable, and Tauri platform prerequisites (WebView2 on Windows; WebKitGTK and GTK/appindicator build dependencies on Linux). See https://v2.tauri.app/start/prerequisites/.

```bash
cd desktop
npm install
npm run dev
npm run tauri dev
```

Build with `npm run build` and `npm run tauri build`. Linux AppImage/DEB are built on Linux runners; Windows NSIS is built on Windows runners. CI uploads packages only after successful build and checksum verification.

## Providers and privacy
Direct public streams use HTMLAudioElement. Audius currently supplies discovery metadata only. YouTube Music, Spotify and JioSaavn are official-service handoffs; no protected stream extraction or DRM bypass is used. Favourites are stored in local storage. No provider tokens or credentials are bundled.

## Packaging status
The workflow is configured, but artifacts are not considered released until the Windows and Linux jobs complete and the checksum verification passes. Code-level scaffolding is not a substitute for manual testing on real Windows/Linux machines.
