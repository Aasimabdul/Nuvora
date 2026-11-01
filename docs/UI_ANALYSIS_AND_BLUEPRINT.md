# Nuvora — MuPlayer Online-First UI Blueprint

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
\n\n## v0.3.8 implementation notes\n\n- Added `NuvoraPlaybackService`, which owns the ExoPlayer and MediaSession for background audio. `MainActivity` connects through a MediaController instead of owning/releasing the playback engine.\n- Added an Up next bottom sheet with tap-to-play, move-up/down reordering and remove actions, plus playback-speed chips and a cancellable sleep timer. The queue is backed by Compose state so edits redraw immediately.\n- The service is declared with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; Media3 manages the playback notification and system transport controls.\n- This phase is an incremental migration. Queue UI, sleep timer/speed controls, ViewModel/Room, trending endpoints, artwork sizing, visual redesign, accessibility, adaptive layouts, provider abstraction and tests remain follow-up work until separately implemented and CI-verified.\n- Background playback applies only to permitted Media3 audio streams (currently Audius). Official YouTube embedded video playback is not converted into background audio.\n