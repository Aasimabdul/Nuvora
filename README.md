<div align="center">

# Nuvora

**An online-first music discovery and streaming app** for Android and desktop, with a shared dark navy / cyan identity.

[![Latest release](https://img.shields.io/github/v/release/Aasimabdul/Nuvora?label=release&color=4D8DFF)](https://github.com/Aasimabdul/Nuvora/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20Linux-48DDEB)

[**Download Android APK**](https://github.com/Aasimabdul/Nuvora/releases/latest) · [**Windows installer**](https://github.com/Aasimabdul/Nuvora/releases/latest) · [**Linux AppImage**](https://github.com/Aasimabdul/Nuvora/releases/latest)

</div>

---

## Nuvora Desktop

The desktop application lives in the separate `desktop/` directory in this repository. It uses Tauri 2, React, TypeScript, Vite and Rust; Android sources under `app/` remain separate and are not migrated.

Desktop builds are packaged on native Windows and Linux GitHub Actions runners. When a version tag is built successfully, the release page receives these assets alongside the Android APK:

- **Windows:** NSIS setup executable (`.exe`)
- **Linux:** AppImage (`.AppImage`) and Debian package (`.deb`)

Download all platform assets from [GitHub Releases](https://github.com/Aasimabdul/Nuvora/releases). The links above open the release page; they do not promise an asset exists until the corresponding release workflow has succeeded.

### Desktop provider support

The initial desktop implementation targets public Audius search/discovery and direct stream playback where available. A search result does not guarantee a playable stream. Spotify, YouTube Music and JioSaavn remain unavailable as native desktop playback providers until a documented and authorized integration is configured; the app must report those limitations rather than simulate playback.

See [desktop/README.md](desktop/README.md) for desktop prerequisites, development commands, build targets and current limitations.

## Android app

Nuvora Android is built with Kotlin and Jetpack Compose. Its existing features include search, source selection, Media3 playback, queue management, local favorites and playlists. Provider capabilities differ, and some existing Android integrations are unofficial and best-effort.

## Android music sources

| Source | Search / discovery | In-app playback |
| --- | --- | --- |
| Audius | Supported | Supported for eligible public streams |
| YouTube Music | Best-effort, unofficial integration | Best-effort; upstream changes may break it |
| JioSaavn | Best-effort, unofficial integration | Best-effort when the response supplies a usable stream |
| Spotify | Official-service handoff | Not provided by Nuvora |
| SoundCloud / Bandcamp | Official-service handoff | Not provided by Nuvora |

Nuvora does not claim to bypass DRM, account requirements, age gates or regional restrictions. Respect each provider's terms and artists' rights.

## Android install and build

1. Open the [GitHub Releases page](https://github.com/Aasimabdul/Nuvora/releases).
2. Download the APK asset for Android.
3. Open it on Android and follow the installation prompt.

Requirements for Android builds: JDK 17, Android SDK (compile SDK 35) and Gradle 8.13.

```sh
gradle --no-daemon assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Repository layout

- `app/` — existing Android application (Kotlin / Jetpack Compose)
- `desktop/` — Tauri 2 desktop application (React / TypeScript / Rust)
- `.github/workflows/android-build.yml` — Android build and release workflow
- `.github/workflows/desktop-build.yml` — native Windows and Linux packaging workflow

## License

Nuvora is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
