# Nuvora — MuPlayer Online-First UI Blueprint

> **Historical document.** This is the original design blueprint written before development. It is kept for reference only. The app name is now **Nuvora**, providers have been integrated, and the sample content described below has been replaced. See the [README](../README.md) for the current feature set.

> Product identity: Android app display name remains **MuPlayer**. Repository: **Nuvora**.
> Product direction confirmed by the owner: **fully online music app**, not a local music player. The music source/provider is intentionally undecided.

## 1. Existing MuPlayer UI audit

Reviewed the existing MuPlayer repository's Compose screens, theme, app manifest, Gradle setup, playback service and view-model direction.

### UX patterns worth retaining as concepts
- Kotlin + Jetpack Compose + Material 3.
- A persistent mini-player and dedicated Now Playing screen.
- Familiar queue, favorites, playlists and theme concepts.
- Media3 as a possible Android playback/session foundation, if compatible with the selected provider.

### What changes for Nuvora
- Online discovery is the primary experience; local MediaStore scanning is not the home screen or product premise.
- Home feed, online search, moods, trending tracks, artists, albums and provider-backed playlists come first.
- Search results and tracks must carry provider/source metadata and handle authentication, rate limits, unavailable tracks and network errors.
- The provider adapter and player integration stay isolated so the design can be developed while the owner decides the source.
- Every visible control should become functional; sample UI controls must be labelled as preview until connected.

## 2. Open-source reference review

Reviewed Rhythm: https://github.com/cromaguy/Rhythm. Its Kotlin/Compose/Material 3 approach offers general UX ideas around consistent components, queue management, lyrics and playback controls. Rhythm is GPL-3.0; Nuvora is implementing an original UI and does not copy its source or distinctive screen implementation. Revisit licensing before any direct code reuse.

## 3. Original visual direction

- Dark navy foundation, electric blue and cyan accents.
- Album-art-led cards with clear typography and strong contrast.
- Subtle glass-like cards, not expensive blur everywhere.
- Rounded surfaces, restrained transitions and lazy lists for performance.
- Loading, offline, no-results, provider-error and sign-in states designed from the start.

## 4. Online-first navigation

- **Home:** curated feed, trending tracks, mood/genre collections and recently played (when a provider supports it).
- **Search:** provider-backed tracks, artists, albums and playlists; filters appear only when the chosen source supports them.
- **Library:** saved/followed tracks, artists, albums and playlists supported by the provider; clearly show account or sync state.
- **Playlists:** browse provider playlists and manage user playlists where API permissions allow.
- **Now Playing:** artwork, title, artist, seek/progress, previous/play-pause/next, queue, shuffle/repeat only where supported, source attribution and lyrics where permitted.
- **Settings:** account/source connection, playback behavior, theme, privacy, cache/download rules, about and licences.
- **Mini-player:** persistent across main tabs once a real stream is active.

## 5. Provider-neutral architecture

- ui/: Compose screens, design tokens, navigation, shared components.
- domain/: track, artist, album, playlist and playback state models.
- providers/: interfaces for catalogue search, metadata, stream/playback integration and account capabilities.
- playback/: source-agnostic queue/session controls and Android background playback.
- data/: settings, favourites, recently played and permitted playlist persistence.
- tests/: UI state, provider mapping, playback state and build smoke tests.

Provider contracts differ substantially. Do not assume a source can expose raw stream URLs or permit third-party playback; implement the selected provider's official supported playback flow.

## 6. Source selection is pending

No provider has been selected or connected yet. Before integration, confirm official/authorized access, API approval, authentication, attribution, playback UI rules, ads, background playback, caching/download restrictions and secret handling.

Do not implement unofficial audio extraction or terms-bypassing playback. If YouTube is considered, follow YouTube API/player policies and do not extract audio or bypass its background-play restrictions.

## 7. Current repository implementation

- Initial Android Gradle project and Compose app shell added.
- Display name is **MuPlayer**; application ID is com.simbuild.nuvora so it can coexist with the older app.
- Home, Search, Library, Playlists, mini-player and Now Playing layouts are represented in the initial UI prototype.
- Current catalogue entries are sample content; buttons change preview UI state only. This is not yet live streaming.
- GitHub Actions workflow added to build the debug APK.

## 8. Next milestones

1. Review and polish the online-first UI.
2. Choose a supported music source/provider.
3. Implement real catalogue search and source attribution.
4. Implement authorized streaming and background session behavior.
5. Connect favorites, playlists, queue, lyrics and settings to provider capabilities.
6. Run CI, fix build/test issues and publish a debug APK artifact.

## Acceptance criteria

- Nuvora stays separate from the original MuPlayer repository.
- App display name remains MuPlayer.
- Online discovery and streaming—not local file scanning—drive the product.
- No fake sample data presented as live provider results.
- Provider rules and attribution are respected.
- No secrets are committed to source control.
- CI builds the APK before a release is claimed.

## Source switcher UX
The Home header includes a visible **Source** control. Its picker currently lists YouTube, JioSaavn, Spotify, SoundCloud and a placeholder for additional sources. This is a UI selection prototype only; it does not mean provider APIs or streaming have been connected.

- Keep the active source visible and make switching available without leaving the Home screen.
- Provider capabilities must remain distinct. YouTube API policies prohibit extracting audio or enabling background playback of YouTube player content; implement only a compliant player/official supported route. See https://developers.google.com/youtube/terms/developer-policies-guide.
- For JioSaavn and other providers, verify authorized API/playback access and terms before promising in-app streaming.

## Implementation log

### v0.6.0 Phase 1 - Playback
- `PlaybackService` (Media3 `MediaSessionService`) owns a dedicated ExoPlayer for official (Audius) tracks; the UI connects with a `MediaController`. Other providers keep the activity-owned player.
- The UI owns the queue (`queueTracks`). Each service track is played on its own; auto-advance and the notification next/previous buttons are routed back to the UI through `PlaybackBridge`. Limitation: if the activity is destroyed while music plays, the current track continues but next/previous and auto-advance stop until the app is reopened.
- `QueueSheet` is provider-agnostic and only edits the UI queue through callbacks.


## 9. Pixel-inspired Liquid Glass + Neomorphism UI overhaul (v0.6.1 in progress)

### Repository audit
- Current implementation source is the Nuvora repository; the MuPlayer repository is a reference for preserving the original app's concepts and identity.
- The current Nuvora UI is concentrated in `MainActivity.kt` (approximately 1,800 lines in the reviewed v0.6.0 source), so broad visual changes should be incremental and extracted into reusable components when safe.
- The current palette list contained five entries: Neon Blue, AMOLED Black, Purple Night, Forest and Light.
- The bottom navigation explicitly forced Material 3 `NavigationBar` to 68dp. This is below the component's normal layout needs and can constrain/clamp its icon and label layout. The mini-player is rendered above the dock in the same bottom bar column.
- Theme switching persisted an integer preference, but custom surfaces and accent colours are still hardcoded in parts of the UI. Full palette coverage needs a follow-up token/component pass rather than claiming every screen is already themed.

### Implemented in this branch
- Retained the five original theme entries in the same order and added new theme palettes: Midnight Navy, Pixel Blue, Liquid Glass, Neo Blue, Arctic Cyan, Aurora, Sunset Coral, Pearl Light, Material You Dynamic, Graphite Silver and Ocean Teal.
- Added a theme metadata flag for light palettes and a dynamic-colour flag. The Material You Dynamic palette uses Android 12+ system dynamic colours when available; older versions fall back to the defined theme palette.
- Replaced the fixed 68dp dock constraint with a responsive minimum-height NavigationBar and explicitly handled navigation-bar insets.
- Updated the dock surface, border, selected indicator and navigation item colours to use the active Material colour scheme.
- Theme preference remains stored under the existing `nuvora_settings/theme_index` key. Existing theme indices are preserved by keeping the original entries first.

### Remaining validation / follow-up
- Build and inspect this branch in GitHub Actions before merging. No build/CI result is claimed by this documentation change alone.
- Capture real emulator/device screenshots after the build; generated design concepts are not implementation screenshots.
- Continue the Liquid Glass/Neomorphism pass for search, cards, mini-player, queue and Now Playing using reusable theme tokens.
- Replace remaining hardcoded `Panel`, `Blue`, `Cyan` and `Muted` usage with theme-aware design tokens where appropriate.
- Verify light-theme contrast, font scaling, dock hit targets, gesture navigation and three-button navigation on small and large screens.
- Add focused tests for theme preference compatibility and dock layout where test infrastructure supports them.


### Follow-up implementation notes
- Replaced the former global fixed colour constants with Material theme tokens for backgrounds, surfaces, primary/secondary accents and muted text. This improves palette consistency across existing custom composables without changing provider/playback logic.
- Added a Settings theme preview showing sample typography, primary/secondary surfaces and dock icons.
- Bumped the app version to 0.6.1 / versionCode 15.
- Full animation redesign and real-device screenshot QA remain outstanding; the theme preview is an in-app preview, not evidence of emulator/device testing.
