# Changelog

All notable changes to Nuvora are documented here. Versions before 1.0.0 were test releases; their highlights are retained below.

## Unreleased

- Updated the Android launcher icon to the Nuvora N mark with cyan-to-blue styling and subtle music-wave details.
- Expanded the README with the current feature list, provider-by-provider capabilities, playback architecture, limitations, install/build instructions and troubleshooting details.
- Screenshots are intentionally not included yet; add real app screenshots after they are provided and verified.

## 1.0.0

First stable version name in the current Gradle configuration. The app includes features developed across the 0.x series:

- Search across YouTube Music, Audius and JioSaavn with a source selector, Songs and Playlists search tabs, and support for pasted/shared YouTube or YouTube Music links.
- Media3 player with floating mini-player, Now Playing screen, seek bar, shuffle, repeat, playback speed and background playback with notification and lock-screen controls where supported.
- Up Next queue sheet with tap-to-play, reorder, swipe-to-remove, Play next and Add to queue.
- Favourites, recently played, play counts, local playlists and best-effort import of public YouTube Music and JioSaavn playlists.
- Sleep timer (presets and custom 1 to 720 minutes), equalizer presets, custom five-band levels and saved user presets.
- Multiple themes including AMOLED Black, Pearl Light and Material You Dynamic colour on Android 12+.
- Downloads for source responses that provide a usable downloadable audio URL, plus separate artwork downloads where supported.
- Back navigation returns to Home first; short transitions between main screens.
- Startup hardening: notification permission prompting and MediaSession binding are deferred until the first screen is shown.

## 0.x history

- **0.5.7**: startup stability fixes, Home discovery rendering, thumbnail parsing and fallbacks, persistent library metadata.
- **0.5.5**: compact Home, updated wordmark, provider icons in the source selector, larger mini-player.
- **0.5.4**: Audius trending endpoint, infinite scroll, pull-to-refresh, loading skeletons, state kept across rotation.
- **0.4.0**: initial best-effort in-app YouTube Music audio playback integration through NewPipe Extractor.
