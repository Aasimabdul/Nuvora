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

## Current development version: v0.6.2

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
