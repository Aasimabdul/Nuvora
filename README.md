# Nuvora

Nuvora is an Android music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Features

- Search and discover tracks from the Audius public catalogue for in-app playback.
- Choose Audius, YouTube Music, JioSaavn, Spotify, or SoundCloud from the source selector.
- Play a YouTube video inside Nuvora using YouTube's official embedded player by pasting a YouTube/YouTube Music video link or sharing a link from YouTube Music to Nuvora. This is video playback, not audio extraction or background playback.
- JioSaavn, Spotify, and SoundCloud searches hand off to the official app or website.
- Stream tracks using Media3 ExoPlayer.
- Save favourites during the current app session.
- Minimal floating bottom navigation with a compact now-playing bar and seek progress.
- Choose from Neon Blue, AMOLED Black, Purple Night, Forest, and Light themes.
- Configure an Audius-compatible HTTPS API base URL in Settings.
- Start a track download from the download icon; Android Download Manager shows progress and saves files under Music/Nuvora.

## Music source and playback support

Audius uses Media3 for in-app audio playback via `https://api.audius.co/v1`. YouTube Music selection provides an official embedded YouTube video player: share a YouTube/YouTube Music link to Nuvora or paste a video URL in the Search tab. This does not provide audio-only extraction or background playback. Some videos may disallow embedding.

| Provider | In-app playback | Current behavior |
| --- | --- | --- |
| Audius | Supported | Search and stream eligible tracks with Media3 through the Audius public API. |
| YouTube / YouTube Music | Embedded video only | Official embedded player; no audio extraction or background playback. |
| JioSaavn | Not configured | Opens official service; direct streaming needs authorized API access. |
| Spotify | Not supported in Media3 | Search opens Spotify. Spotify App Remote does not provide Spotify audio streams to Nuvora. |
| SoundCloud | Not configured | In-app playback requires registered API access and authorized playback. |
| Bandcamp | External handoff | Search opens the official service. |

In Settings, choose **Music source** and configure an HTTPS base URL for an Audius-compatible API if needed. The endpoint must provide the expected tracks search response and stream routes. Additional providers will only be integrated when their official developer access permits the intended playback experience.

## Downloads and rights

Downloads depend on provider responses, availability, and track permissions. Only download tracks when you have permission to do so. A download request may fail if the provider does not permit a stream to be saved as a file.

## Build and verify

The project uses Gradle and Java 17. To build a debug APK locally:

```bash
gradle --no-daemon assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and attaches `Nuvora-debug.apk` to releases. A successful compile does not replace device testing: verify Audius search/playback, external provider handoff, YouTube embedding, downloads, theme persistence, and playback controls on a real Android device.

## Current release: v0.3.6

- Added a time-based greeting, mood-based search cards, recently played tracks, horizontal music discovery cards, and a dedicated Discover destination.
- Added Settings toggles to show or hide mood cards, recently played, and the horizontal carousel on Home.
- Preserved existing music-source selection, themes, favourites, downloads, floating mini-player, and navigation.
- YouTube remains limited to the official embedded player; native audio-only/background playback is not supported by the official YouTube API.
