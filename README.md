<div align="center">

# Nuvora

**An online-first Android music discovery and streaming app** built with Kotlin and Jetpack Compose. Search supported music sources from one place, play eligible tracks, manage your queue and library, and customise the player with a dark navy / cyan-blue look.

[![Latest release](https://img.shields.io/github/v/release/Aasimabdul/Nuvora?label=release&color=4D8DFF)](https://github.com/Aasimabdul/Nuvora/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[**Download the latest APK**](https://github.com/Aasimabdul/Nuvora/releases/latest) · [Report a bug](https://github.com/Aasimabdul/Nuvora/issues)

</div>

---

## About Nuvora

Nuvora brings music discovery from several online services into one Android interface. The app has a shared search experience, source selector, player and queue, plus on-device favourites and playlists. What can actually play depends on the provider: some sources expose playable public streams, while others only support opening their official service.

> **Current branding:** Nuvora is the repository and app name. The launcher icon uses the N mark, cyan-to-blue colour, and subtle audio-wave details. Product screenshots will be added here after real device screenshots are supplied and checked.

## Features in the app

| Area | Current functionality |
| --- | --- |
| **Home & discovery** | Music discovery sections, Audius trending results, pull-to-refresh, paginated loading where supported, and loading placeholders |
| **Search** | Source selector and Songs / Playlists search flows for supported providers; accepts shared or pasted YouTube / YouTube Music links |
| **Playback** | Media3 / ExoPlayer playback for playable results, progress/seek controls, previous/next, shuffle, repeat and playback speed where available |
| **Mini-player & Now Playing** | Persistent mini-player and a dedicated full player screen |
| **Background audio** | MediaSession service for supported Audius playback, with notification, lock-screen and compatible Bluetooth/system media controls |
| **Queue** | Up Next sheet, play a queued track, reorder, swipe to remove, clear upcoming tracks, Play next and Add to queue |
| **Library** | Favourites, recently played history, play counts and local playlists stored on the device |
| **Playlist discovery** | Best-effort import/browsing of public YouTube Music and JioSaavn playlists; support depends on source response and playlist accessibility |
| **Sound settings** | Sleep timer with presets/custom duration, equalizer presets, custom five-band levels and saved custom presets |
| **Themes** | Multiple colour palettes including Neon Blue, AMOLED Black, Purple Night, Forest, Aurora, Pearl Light, Graphite Silver and Material You Dynamic colour on Android 12+ |
| **Downloads** | Download action is available only where the provider response supplies a usable downloadable URL. Availability and permitted use depend on the source; artwork is stored separately when supported |

## Music sources: what works and how

Nuvora uses provider-specific adapters for catalogue results. A result is mapped into the app's common track/playlist model, then the app hands a playable URL to the player when the provider flow supplies one. The app does **not** have a universal stream URL for every service.

| Source | Search / discovery | In-app playback | How it is integrated |
| --- | --- | --- | --- |
| **Audius** | Supported | Supported for available public tracks | Uses the public Audius API for search/trending metadata and its stream endpoint. Media3's playback service supports background playback and system media controls. |
| **YouTube Music** | Best-effort | Best-effort; may fail when upstream changes | Uses NewPipe Extractor rather than an official YouTube Music playback API. This is an unofficial integration and can break. It must not be treated as an official or guaranteed integration. |
| **JioSaavn** | Best-effort | Best-effort when a usable stream URL is returned | Uses a community-maintained, unofficial endpoint. It is not an official JioSaavn API integration; endpoint shape, access and availability can change. |
| **Spotify** | Opens the official service | Not provided by Nuvora | The app does not extract Spotify audio. |
| **SoundCloud** | Opens the official service | Not provided by Nuvora | No approved in-app streaming integration is configured. |
| **Bandcamp** | Opens the official service | Not provided by Nuvora | Playback remains with the service. |

### Important playback notes

- Search results do not guarantee a playable track. A provider can return metadata without a stream URL, or a stream can fail because of network, region, account, age, rate-limit or provider-side changes.
- Background playback and system controls are strongest for the Audius MediaSession service. Do not assume every source has identical background/queue behaviour.
- YouTube Music and JioSaavn support is unofficial and best-effort. Nuvora is not affiliated with those services and does not promise uninterrupted access.
- Nuvora does not claim to bypass DRM, account requirements, age gates or regional restrictions. Respect each provider's terms and artists' rights.

## Library and privacy

- Favourites, recent history, play counts, and local playlists are stored on the device.
- An account is not required by Nuvora itself.
- Network access is used to contact the selected music services and load track metadata/artwork/streams.
- Third-party providers have their own privacy policies and may process requests according to their terms.

## Install

1. Open the [GitHub Releases page](https://github.com/Aasimabdul/Nuvora/releases).
2. Download the APK asset attached to the release.
3. Open it on Android. If prompted, allow installation from the browser or file manager you used.
4. Launch Nuvora and choose a source to search.

**Minimum Android version:** Android 8.0 (API 26) or newer.

Release APKs are built by GitHub Actions from this repository. The CI workflow currently produces a debug APK for testing. Always download builds from this repository's Releases page.

## Build from source

Requirements: JDK 17, Android SDK (compile SDK 35), and Gradle 8.13.

```bash
git clone https://github.com/Aasimabdul/Nuvora.git
cd Nuvora
gradle --no-daemon assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds on pushes to `main` and on version tags beginning with `v` or `V`. A release asset is published only when the workflow's release conditions are met and the build succeeds.

## Technical overview

- **Language / UI:** Kotlin, Jetpack Compose, Material 3
- **Playback:** AndroidX Media3 / ExoPlayer and MediaSession
- **Networking / provider adapters:** provider-specific repository classes for Audius, YouTube Music and JioSaavn
- **Artwork:** Coil Compose
- **App ID:** `com.simbuild.nuvora`
- **Minimum SDK:** 26
- **Current version in Gradle:** `1.0.0` (versionCode `24`)

The app uses a shared `MusicTrack` model for provider results, while provider adapters keep each service's response parsing separate. This makes the UI more consistent, but provider capabilities and playback rules still differ.

## Known limitations

- YouTube Music and JioSaavn use unofficial integrations and can stop working when their upstream services change.
- Spotify, SoundCloud and Bandcamp are external-service handoffs, not in-app audio streaming sources.
- Playlist import depends on a public, accessible playlist and the provider endpoint returning the expected data.
- Downloads are not available for every source or every track.
- Real-device playback, UI and release APK checks should be completed for each release; a successful source commit alone is not proof of a successful build.

## Contributing and bug reports

Found a bug? [Open an issue](https://github.com/Aasimabdul/Nuvora/issues). Include:
- Nuvora version and installation source
- Android version and device model
- Source selected and track/playlist name
- What you expected, what happened, and any visible error message

Please do not attach private account credentials or tokens.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Licence and third-party notices

Nuvora is licensed under the **GNU General Public License v3.0**; see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Nuvora is an independent project and is not affiliated with or endorsed by YouTube, Google, Audius, JioSaavn, Spotify, SoundCloud or Bandcamp. All trademarks belong to their respective owners.
