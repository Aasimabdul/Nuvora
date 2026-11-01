# Nuvora

**Nuvora** is a lightweight Android music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3. It brings supported music sources, search, playlists, and playback controls into one dark, cyan-accented interface.

> **Release status:** the latest manually tested APK in this repository workflow is a debug build (`0.5.8-debug`). It is not yet the signed production `v1.0.0` release. A release build must be configured and signed with a dedicated release key before it is distributed as stable.

## Highlights

- Dark navy interface with cyan-blue accents, provider selector, floating mini-player, and Now Playing screen.
- Search and source discovery across supported providers.
- Media3 playback controls, queue navigation, seek/progress, shuffle/repeat, sleep timer, and background media notification.
- Local favourites, recently played tracks, play counts, and manually managed playlists.
- Best-effort public playlist search/import where supported.
- Direct-file downloads only when a source supplies a downloadable audio URL; protected/provider watch pages are not treated as audio files.
- Android 8.0 (API 26) or newer; built with Java 17 and Gradle.

## Supported sources and limitations

| Source | Search | In-app playback | Notes |
| --- | --- | --- | --- |
| YouTube Music | Yes; public playlist search is best-effort | Best-effort | Uses NewPipe Extractor and undocumented service behaviour, not an official YouTube Music API. Availability can change. |
| Audius | Yes | Eligible public tracks | Depends on the public API and creator-provided availability. |
| JioSaavn | Yes | Best-effort where a stream URL is returned | Uses a community endpoint, not an official developer streaming API. |
| Spotify | External handoff | No general-purpose in-app streaming | Opened externally; general audio streams are not exposed for custom playback. |
| SoundCloud | External handoff | Not enabled as general in-app streaming | Requires supported/approved integration. |
| Bandcamp | External handoff/search | No general-purpose in-app streaming | Playback remains with the service. |

Nuvora does not intentionally bypass DRM, sign-in, age gates, regional restrictions, or other access controls. Playback may fail for specific tracks because of provider changes, rights, region, or availability. Use each service in accordance with its terms and applicable rights.

## Build from source

Requirements: JDK 17 and Gradle 8.13 (the GitHub Actions workflow installs Gradle).

```bash
gradle --no-daemon assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

A successful CI compile verifies build integration, not playback on every device or every track. Test on a real Android device before distributing a build. Do not describe the debug APK as a production release.

## Release and F-Droid

- GitHub releases should contain a verified, installable APK with clear versioning and release notes.
- Production APKs must be signed with a dedicated release key; do not use a CI debug key as the production signing identity.
- F-Droid submission requires source metadata and a licensing/dependency/packaging review. Submission is reviewed by F-Droid; acceptance is not automatic.
- Current repository licence: GNU GPL-3.0. Confirm that all bundled assets and dependencies are compatible before submission.

## Current tested build

- Version: `0.5.8-debug` (test build, not a stable release).
- Manual test report supplied on 2026-11-01: installation/startup, UI, playback, playlists/download handling, and stability checks all passed on one test device.
- This manual report is not a substitute for automated tests or testing across Android versions/devices.

---
## Playback and search

- **YouTube Music is the default source.** The Home search bar searches YouTube Music; result rows have a Play action to start in-app audio playback through Media3.
- **YouTube Music audio resolution is unofficial.** Nuvora uses NewPipe Extractor to resolve publicly available YouTube audio streams. YouTube can change its endpoints; some tracks may fail because of regional restrictions, age restrictions, sign-in requirements, stream formats, or availability. This is not an official YouTube Music API.
- Audius catalogue search and eligible track playback through its public API.
- JioSaavn search and community API integration where stream URLs are returned. Community endpoints may change.
- Spotify, SoundCloud and Bandcamp are external handoff sources in this build; they do not provide general-purpose stream URLs for Media3 playback.
- Shared Media3 player with in-app controls, seek bar, and recently-played list.
- Share/paste YouTube and YouTube Music links to find the corresponding video in Nuvora.
- Persistent favourites, recent tracks and most-played counts; manual playlists can be created from individually selected search results and edited by adding selected tracks.
- YouTube Music playlist search/import is best-effort through an undocumented public endpoint; imported public playlist tracks are stored in Nuvora's local library.
- Floating mini-player, immersive Now Playing screen, seek/progress controls, queue navigation, shuffle, repeat, and a 15/30/45/60-minute sleep timer.
- Downloads are shown only for sources that provide a direct stream URL; downloading is subject to the source's permissions and terms.

## Music source and playback support

| Provider | Search | In-app playback | Notes |
| --- | --- | --- | --- |
| YouTube Music | Songs and best-effort public playlist search | Best-effort via NewPipe Extractor + Media3 | Unofficial; public playlists can be imported locally; some streams/playlists may fail when YouTube changes its implementation. |
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

## Current version: v0.5.7

### v0.5.7 startup stability and release automation

- Defers the optional notification permission prompt until the first UI view is attached.
- Defers Media3 MediaSession binding until after the initial UI setup, so a session-connection failure does not block the local player screen.
- Makes MediaSession startup failures visible in Logcat while keeping local playback available, and guards player cleanup during partial Activity initialization.
- Updates the GitHub Actions release workflow to derive the tag and release name from `versionName`, avoiding stale hardcoded release tags.
- This is a defensive startup fix based on static source review. A device-specific crash cannot be confirmed without the affected APK's Logcat stack trace; verify cold launch on a physical Android device.

- Fixed Home discovery/trending so YouTube Music search results render in the Home sections instead of blank catalogue cards.
- Fixed nested YouTube Music thumbnail parsing, added YouTube video-thumbnail fallbacks, normalized CDN image URLs, improved image-load fallbacks, and migrated saved YouTube tracks that lacked artwork.
- Improved direct-stream HTTP headers and playback error/retry feedback. Playback remains best-effort because YouTube does not provide a general official public audio-stream API.
- Added persistent library metadata, favourites, recent history, and most-played counts.
- Added local playlist creation from manually selected tracks and playlist import attempts for public YouTube Music/JioSaavn playlists.
- Restyled the source selector, floating glass mini-player/navigation, and Now Playing screen; added seek/progress, shuffle, repeat, queue navigation, and sleep timer.
- Preserved Audius, JioSaavn, theme options, and the existing navigation.

## Third-party notices

The project uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), licensed under GNU GPL version 3 or later. See `THIRD_PARTY_NOTICES.md` and `LICENSE`.

## v0.6.0 (in progress)
- Phase 1: Audius playback now runs in a Media3 `MediaSessionService`, so it continues in the background with notification, lock-screen and Bluetooth controls. The new "Up next" sheet (open it from Now Playing) lets you tap to play, long-press the handle to reorder, swipe to remove and clear upcoming tracks. Every track has a menu with Play next, Add to queue and Share, and Now Playing gains playback-speed chips.
- Scope note: only the official Audius source uses the background service. Other providers keep their existing in-app foreground player.


## v0.6.1 UI overhaul (in progress)

- Preserved the five original Nuvora theme entries and added selectable palettes for Midnight Navy, Pixel Blue, Liquid Glass, Neo Blue, Arctic Cyan, Aurora, Sunset Coral, Pearl Light, Material You Dynamic, Graphite Silver and Ocean Teal.
- Theme indices for the original palettes remain in their original order so saved preferences continue to map to the same themes.
- Added Android 12+ system dynamic colour support for the Material You Dynamic theme, with a normal palette fallback on older Android versions.
- Fixed the bottom navigation sizing constraint: the dock no longer forces Material 3 NavigationBar into a 68dp height that can clip its icon/label layout. It uses a responsive minimum height and explicit system-bar inset handling.
- Updated the dock surface and selected/unselected item colours to follow the active Material theme.
- Replaced legacy fixed custom UI colours with active Material theme tokens so the new palettes affect more of the interface consistently.
- Added a live Settings theme preview with sample cards, typography, accent surfaces and dock icons.
- This is an incremental UI foundation. The complete Liquid Glass/Neomorphism treatment across every screen, motion polish, and device/emulator QA still require validation before release.


## v0.6.2 fixes in progress

- Adds Songs and Playlists tabs to the Search screen; playlist discovery is moved out of the Playlists/Library search area.
- Replaces the theme dropdown with a swatch-based theme picker sheet and applies theme-aware switch colours.
- Makes Settings vertically scrollable.
- Displays a download action consistently, while explaining that direct downloads are unavailable for YouTube Music instead of trying to download a watch-page URL.
- Wraps Now Playing transport controls in a translucent theme-aware glass surface.
- Routes playable tracks through Media3 MediaSession when connected, enabling Android media notification/lock-screen controls for supported streams across providers. Validate this on-device.
- In-place APK updates require a consistent signing certificate. CI accepts a stable debug keystore via the `NUVORA_DEBUG_KEYSTORE_BASE64` repository secret. Do not commit the private keystore; without this secret, debug APK signatures may differ between CI runs.


## v0.6.3 player usability updates

- Reduces crowding on track rows: favourite and More actions remain visible while queue, playlist, download and equalizer actions move into the overflow menu.
- Adds local playlist creation and add-to-playlist actions from track and Now Playing menus.
- Adds a custom sleep timer (1–720 minutes), built-in equalizer presets, custom five-band levels and saved user presets.
- Adds a subtle animated gradient to Now Playing.
- Downloads available artwork separately to Pictures/Nuvora/Artwork alongside eligible audio downloads in Music/Nuvora.
- Audio-effect behavior and album art downloads depend on Android device and provider support; verify on-device.


## v0.6.4 UI and equalizer fixes

- Replaced the track More popup and Now Playing options popup with a shared, theme-aware action-sheet presentation.
- Provider and theme selection use full-width bottom sheets rather than small popup menus.
- Equalizer changes target both the Media3 service session and the local ExoPlayer session. The effect is re-created when the actual audio-session ID becomes available and settings are re-applied when playback reaches READY.
- Improved hierarchy and descriptions for Play next, Queue, Playlist, Download, Equalizer, Sleep timer and Share actions.
- CI build required before APK distribution; OEM equalizer support still needs real-device validation.


## v0.6.5 navigation and light-theme polish

- System Back from any non-home screen now returns to Home instead of immediately leaving the app.
- Added a short crossfade transition when switching main screens.
- Removed fixed dark navy colors from the mini-player gradient, Now Playing backdrop and active track row so Light/Pearl themes use their own Material color scheme.
- Uses theme-aware play icon contrast via `onPrimary`.

## v0.5.5 UI polish

- Removed the “Find your frequency” headline and its repeated greeting to make Home more compact.
- Updated the Nuvora wordmark so “NU” uses cyan blue while “VORA” follows the active theme text colour.
- Added provider-specific favicon artwork to the audio-source selector.
- Increased the floating mini-player artwork, song typography, padding and seek-bar height for easier touch interaction.
- Kept existing provider selection, playback flow, navigation and theme architecture intact.

## v0.5.4
- Audius Home now uses the official trending endpoint instead of a search for "electronic chill".
- Infinite scroll for Audius trending and search results (pages of 25 tracks), without disturbing the current queue.
- Pull-to-refresh on Home and animated loading skeletons for the track lists.
- The app no longer restarts on rotation, so your tab, search text and results are kept.
