<div align="center">

# Nuvora

**A free, open-source Android music app: search, stream and organise music from one dark, themeable player.**

[![Latest release](https://img.shields.io/github/v/release/Aasimabdul/nuvora?label=release&color=4D8DFF)](https://github.com/Aasimabdul/nuvora/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[**Download the latest APK**](https://github.com/Aasimabdul/nuvora/releases/latest)

</div>

---

## Why Nuvora?

- **One place for your music.** Search YouTube Music, Audius and JioSaavn from a single search bar, with a source selector to switch providers.
- **A proper player.** Floating mini-player, full Now Playing screen, queue, shuffle, repeat, playback speed, sleep timer and equalizer.
- **Looks the way you want.** 16 themes, from AMOLED Black to Pearl Light, plus Material You dynamic colour on Android 12+.
- **Yours, locally.** Favourites, recently played, play counts and playlists are stored on your device. No account and no ads.
- **Open source.** Licensed under GPL-3.0. Read it, build it, improve it.

## Download and install

1. Open the [latest release](https://github.com/Aasimabdul/nuvora/releases/latest) on your phone and download the `.apk` file.
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Launch **Nuvora** and search for a song.

Requires **Android 8.0 (API 26) or newer**.

> **About the APK:** release APKs are built by GitHub Actions from this repository. They are signed with the project's CI key, not distributed through Google Play. Always download Nuvora from this repository's Releases page.

## Features

| Area | What you get |
| --- | --- |
| **Search** | Songs and public playlists tabs, source selector, paste or share a YouTube / YouTube Music link to open it in Nuvora |
| **Home** | Trending and discovery sections, pull-to-refresh, infinite scroll for Audius, loading skeletons |
| **Player** | Media3 playback, seek bar, previous / next, shuffle, repeat, playback speed, background playback with notification and lock-screen controls |
| **Queue** | "Up next" sheet: tap to play, reorder, swipe to remove, clear upcoming tracks, Play next and Add to queue |
| **Library** | Favourites, recently played, most-played counts, local playlists, import of public YouTube Music and JioSaavn playlists |
| **Sound** | Sleep timer (presets or custom 1 to 720 minutes), equalizer presets, custom five-band levels, saved user presets |
| **Themes** | 16 palettes including Neon Blue, AMOLED Black, Purple Night, Forest, Aurora, Pearl Light, Graphite Silver and Material You Dynamic |
| **Downloads** | Direct downloads for sources that provide a downloadable audio URL (audio to `Music/Nuvora`, artwork to `Pictures/Nuvora/Artwork`) |

## Music sources

| Source | Search | In-app playback | Notes |
| --- | --- | --- | --- |
| YouTube Music | Yes | Best-effort | Uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor). Unofficial, so it can break when YouTube changes. Direct downloads are not offered. |
| Audius | Yes | Yes, for eligible public tracks | Public Audius API. Background playback runs in a Media3 service. |
| JioSaavn | Yes | Best-effort | Community endpoint, not an official streaming API. May change. |
| Spotify | Opens externally | No | Spotify does not expose general audio streams for third-party playback. |
| SoundCloud | Opens externally | No | Needs an approved integration. |
| Bandcamp | Opens externally | No | Playback stays with the service. |

## Good to know

- Playback depends on third-party services. Some tracks can fail because of region, age or sign-in restrictions, or because a provider changed its behaviour.
- Nuvora does not bypass DRM, sign-in, age gates or regional restrictions. Please respect the terms of each service and the rights of artists.
- Nuvora has no analytics and does not require an account. Network access is used only to talk to the music providers above.

## Build from source

Requirements: JDK 17, Android SDK (compile SDK 35) and Gradle 8.13.

```bash
git clone https://github.com/Aasimabdul/nuvora.git
cd nuvora
gradle --no-daemon assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds every push. Pushing a tag that starts with `v` (for example `v1.0.0`) builds the APK and attaches it to a GitHub release.

## Tech stack

Kotlin, Jetpack Compose (Material 3), AndroidX Media3 (ExoPlayer and MediaSession), Coil, NewPipe Extractor.

## Contributing and feedback

Found a bug or have an idea? [Open an issue](https://github.com/Aasimabdul/nuvora/issues). When reporting a playback problem, please include the source (YouTube Music, Audius or JioSaavn), the track name, your Android version and device.

If you like Nuvora, a star on the repository helps other people find it.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Licence and notices

Nuvora is licensed under the **GNU General Public License v3.0**, see [LICENSE](LICENSE). It uses NewPipe Extractor (GPL-3.0-or-later), see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Nuvora is an independent project. It is not affiliated with or endorsed by YouTube, Google, Audius, JioSaavn, Spotify, SoundCloud or Bandcamp. All trademarks belong to their owners.
