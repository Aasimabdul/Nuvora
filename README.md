# Nuvora

Nuvora is an Android music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Playback and search

- **YouTube Music is the default source.** The Home search bar searches YouTube Music; result rows have a Play action to start in-app audio playback through Media3.
- **YouTube Music audio resolution is unofficial.** Nuvora uses NewPipe Extractor to resolve publicly available YouTube audio streams. YouTube can change its endpoints; some tracks may fail because of regional restrictions, age restrictions, sign-in requirements, stream formats, or availability. This is not an official YouTube Music API.
- Audius catalogue search and eligible track playback through its public API.
- JioSaavn search and community API integration where stream URLs are returned. Community endpoints may change.
- Spotify, SoundCloud and Bandcamp are external handoff sources in this build; they do not provide general-purpose stream URLs for Media3 playback.
- Shared Media3 player with in-app controls, seek bar, and recently-played list.
- Share/paste YouTube and YouTube Music links to find the corresponding video in Nuvora.
- Favourites during the current app session, theme options, discovery cards, playlists tab, and downloads where a source provides a downloadable URL.

## Music source and playback support

| Provider | Search | In-app playback | Notes |
| --- | --- | --- | --- |
| YouTube Music | Yes | Best-effort via NewPipe Extractor + Media3 | Unofficial; may break when YouTube changes its implementation. |
| Audius | Yes | Yes, for eligible public streams | Public API stream availability and rights are controlled by creators. |
| JioSaavn | Yes | Best-effort for returned stream URLs | Community endpoint; not an official developer streaming API and may change. |
| Spotify | External handoff | No | Public APIs do not expose general audio streams for custom playback. |
| SoundCloud | External handoff | No | Requires approved API access and supported playback integration. |
| Bandcamp | External handoff | No | External search only. |

## Build

The project uses Gradle and Java 17:

```bash
gradle --no-daemon assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds a debug APK for testing. A successful compile confirms build integration, not successful playback on every device or for every song; test actual streams on an Android device.

## Important limitations

YouTube Music support relies on undocumented service behavior and can stop working without notice. Nuvora does not bypass DRM, account checks, age restrictions, regional availability, or other access controls. Streams may be unavailable for some videos. Respect the rights and terms associated with each service.

## Current development version: v0.4.0

- Set YouTube Music as the default source, including for Home search.
- Added in-app YouTube Music result playback by resolving a playable audio stream and passing it to Media3.
- Added NewPipe Extractor as the open-source stream-resolution component.
- Added result-level Play actions, upgraded preference migration, and a YouTube Music-specific search placeholder.
- Preserved Audius, JioSaavn, themes, discovery, and existing navigation.

## Third-party notices

The project uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), licensed under GNU GPL version 3 or later. See `THIRD_PARTY_NOTICES.md` and `LICENSE`.
