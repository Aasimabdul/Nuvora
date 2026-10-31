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

## Music source

Audius uses Media3 for in-app audio playback via `https://api.audius.co/v1`. YouTube Music selection provides an official embedded YouTube video player: find a track in YouTube Music and use Share → Nuvora, or copy its video link and paste it in the Search tab. This does not provide audio-only extraction or background playback. JioSaavn, Spotify, and SoundCloud still open their official apps/sites. In the app, open **Settings → Music source**, enter an HTTPS base URL for an Audius-compatible API, and save it. The endpoint must provide the expected tracks search response and stream routes.

## Provider support

| Provider | In-app playback | Current behavior |
| --- | --- | --- |
| Audius | **Supported** | Search and stream eligible tracks with Media3 through the Audius public API. |
| YouTube Music | **Embedded video supported** | Share a YouTube/YouTube Music link to Nuvora or paste a video URL in the Search tab to play through the official YouTube embedded player. No audio-only extraction or background playback; some videos may disallow embedding. |
| JioSaavn | Not configured | Search opens the official JioSaavn service. Research found public community APIs are explicitly unofficial; JioSaavn terms prohibit automated/unofficial access unless separately authorized. We need written partner authorization/API documentation before adding direct playback. |
| Spotify | Not supported in Media3 | Search opens Spotify. Spotify's Android App Remote can control the official Spotify app, but does not provide Spotify audio streams to Nuvora. |
| SoundCloud | Not configured | Search opens SoundCloud. In-app playback would require registered API access, authorized playback, and required attribution. |

Audius is the in-app audio-streaming provider. YouTube is supported as an official embedded video player only. JioSaavn direct streaming is not enabled because no public official partner playback SDK/API was verified. The selector describes this distinction in Settings, and selecting an external-only provider clears Audius search results so they are not mistaken for that provider's catalogue. Additional providers will only be integrated when their official developer access permits the intended playback experience.


## Downloads and rights

Downloads depend on the provider's response, availability, and applicable track permissions. Only download tracks when you have permission to do so. A download request may fail if the provider does not permit the stream to be saved as a file.

## Build

The project uses Gradle and Java 17. To build a debug APK locally:

```bash
gradle --no-daemon assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and attaches `Nuvora-debug.apk` to releases.

## Testing note

This is an early release. Verify search, playback, theme persistence, and downloads on a real Android device before relying on it. YouTube embedded playback requires network access and may be unavailable for videos whose owners disable embedding. Provider availability and playback can vary by track and network.


## v0.3.6
- Added a time-based greeting, mood-based search cards, recently played tracks, horizontal music discovery cards, and a dedicated Discover destination.
- Added Settings toggles to show or hide mood cards, recently played, and the horizontal carousel on Home.
- Preserved existing music-source selection, themes, favourites, downloads, floating mini-player, and navigation.
- YouTube remains limited to the official embedded player; native audio-only/background playback is not supported by the official YouTube API.

## v0.3.7
- Real playback queue: next/previous work across the current list, plus shuffle and repeat (off/all/one) on the Now Playing screen.
- Now Playing now has a seek bar with elapsed/total time and Save/Download buttons.
- Favourites are saved on the device and no longer disappear when you search again.
- Light theme now uses theme-aware surface and text colours instead of dark panels.
- Active track is highlighted in lists; search box has a clear button; Settings scrolls on small screens; system Back returns Home from other tabs.
- YouTube embedded player screen has a Paste button and rounded player.
- Provider policy unchanged: no unofficial audio extraction (YouTube Music stays embedded-only; JioSaavn stays external-only).
