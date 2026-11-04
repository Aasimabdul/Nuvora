<div align="center">

# Nuvora — Free, Open-Source Android Music Player

**Discover music. Keep your listening flow simple.** Nuvora is a free, open-source Android music discovery and player app built with Kotlin and Jetpack Compose. Search supported online music sources, play tracks when a compatible stream is available, manage your queue and personal library, and use a modern dark navy / cyan-blue interface.

[![Latest release](https://img.shields.io/github/v/release/Aasimabdul/Nuvora?label=latest%20release&color=42bff5)](https://github.com/Aasimabdul/Nuvora/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

[**Download Android APK**](https://github.com/Aasimabdul/Nuvora/releases/latest/download/Nuvora_Music.apk) · [Official website](https://aasimabdul.github.io/Nuvora/) · [All releases](https://github.com/Aasimabdul/Nuvora/releases) · [Report a bug](https://github.com/Aasimabdul/Nuvora/issues)

</div>

---

## What is Nuvora?

Nuvora is an independent Android music player project focused on **online music discovery, supported in-app playback, and a personal listening library**. It brings selected music sources into one interface without pretending that every provider offers the same capabilities.

- **Free and open source:** licensed under GNU GPL-3.0.
- **Android-first:** minimum Android 8.0 (API 26).
- **Modern interface:** Kotlin, Jetpack Compose, Material 3, dark navy / cyan styling, and multiple theme palettes.
- **Music controls:** queue management, a persistent mini-player, favourites, local playlists, sleep timer and equalizer.
- **Honest source support:** a search result does not guarantee that a provider will return a playable audio stream.

## Features

| Area | Available functionality |
| --- | --- |
| **Search & discovery** | Source selector, song and playlist search flows, Audius trending discovery, and support for pasted/shared YouTube or YouTube Music links |
| **Playback** | Media3 / ExoPlayer controls for compatible streams, progress seeking, previous/next, shuffle, repeat and playback speed where available |
| **Mini-player & player screen** | Persistent mini-player and a dedicated Now Playing screen |
| **Background audio** | MediaSession notification, lock-screen and compatible system/Bluetooth controls for supported playback flows |
| **Queue** | Play next, add to queue, reorder, remove tracks, clear upcoming tracks and tap a queued track to play it |
| **Personal library** | Favourites, recently played history, play counts and local playlists stored on the device |
| **Playlists** | Best-effort browsing/import of accessible public YouTube Music and JioSaavn playlists |
| **Sound settings** | Sleep timer, equalizer presets, custom five-band levels and saved custom presets |
| **Themes** | Multiple colour palettes, including Neon Blue, AMOLED Black, Purple Night, Forest, Aurora, Pearl Light and Material You Dynamic colour on Android 12+ |
| **Downloads** | A download option appears only when the provider response supplies a usable downloadable URL; it is not available for every track |

## Music source compatibility

Provider support can change independently of Nuvora. Please read these notes before installing:

| Source | Search / discovery | Nuvora in-app playback | Notes |
| --- | --- | --- | --- |
| **Audius** | Supported | Supported for available public tracks | Uses public Audius endpoints for metadata and streams. |
| **YouTube Music** | Best-effort | Best-effort; can fail | Uses NewPipe Extractor, not an official YouTube Music playback API. This integration is unofficial and may break when upstream services change. |
| **JioSaavn** | Best-effort | Best-effort when a usable stream URL is returned | Uses a community-maintained, unofficial endpoint; availability and response format can change. |
| **Spotify** | External service hand-off | Not provided by Nuvora | Nuvora does not extract Spotify audio. |
| **SoundCloud / Bandcamp** | External service hand-off | Not provided by Nuvora | Playback remains with the respective service. |

**Important:** Search results can contain metadata without a playable stream. Playback can also be affected by a provider's network, region, account, age, rate-limit or service changes. Nuvora does not claim to bypass DRM, account requirements, age restrictions or regional restrictions. Respect each service's terms and artists' rights.

Nuvora is an independent project and is not affiliated with or endorsed by YouTube, Google, Audius, JioSaavn, Spotify, SoundCloud or Bandcamp. Trademarks belong to their respective owners.

## Download and install

1. Open the [latest Nuvora release](https://github.com/Aasimabdul/Nuvora/releases/latest).
2. Download **Nuvora_Music.apk** from the release assets.
3. Open the downloaded APK on your Android device. If Android prompts you, allow your browser or file manager to install apps from that source.
4. Open Nuvora, choose a supported source, and search for music.

**Minimum version:** Android 8.0 (API 26) or newer.  
**Cost:** Free to download. Nuvora does not require a Nuvora account. Third-party services may have their own accounts, requirements or terms.

The latest public GitHub release and the newest code on the default branch are not always the same version. Check the release notes and release assets before installing; a successful source-code change is not proof of a tested public release.

## Build from source

Requirements: JDK 17, Android SDK (compile SDK 35), and Gradle 8.13.

```bash
git clone https://github.com/Aasimabdul/Nuvora.git
cd Nuvora
gradle --no-daemon assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions may build test APKs from repository changes and publish release assets when the relevant workflow conditions are met. For normal installation, use the APK attached to a published GitHub Release.

## Technical overview

- **Language and UI:** Kotlin, Jetpack Compose, Material 3
- **Playback:** AndroidX Media3 / ExoPlayer and MediaSession
- **Provider adapters:** Audius, YouTube Music and JioSaavn source adapters
- **Artwork:** Coil Compose
- **Application ID:** `com.simbuild.nuvora`
- **Minimum SDK:** 26
- **License:** GNU GPL v3.0

Nuvora uses a shared `MusicTrack` model for provider results, while provider adapters keep each service's response parsing separate. The interface is consistent across sources, but the playback rules and availability are not identical.

## Privacy

- Favourites, recent history, play counts and personal playlists are stored on the device.
- Nuvora itself does not require an account.
- Network access is used to query music services and load the metadata, artwork and streams those services make available.
- Third-party services apply their own privacy policies and terms.

## Known limitations

- YouTube Music and JioSaavn use unofficial, best-effort integrations and may stop working when upstream services change.
- Spotify, SoundCloud and Bandcamp are external-service hand-offs, not in-app audio streaming sources.
- Playlist browsing depends on public accessibility and provider response formats.
- Downloads are only offered where a compatible downloadable URL is returned.
- Device-level playback, performance and background behaviour can vary by Android version and provider.

## Bug reports and contributions

Found a problem? [Open a GitHub issue](https://github.com/Aasimabdul/Nuvora/issues). Please include the Nuvora version, Android version, device model, selected source, track/playlist name, expected behaviour and what actually happened. Do not attach passwords, private account credentials or tokens.

See [CHANGELOG.md](CHANGELOG.md) for version history and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party notices.

## License

Nuvora is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full license text.
