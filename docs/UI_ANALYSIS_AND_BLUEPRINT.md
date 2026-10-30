# Nuvora — MuPlayer UI Analysis & Build Blueprint

> Product identity: Android app display name remains **MuPlayer**. Repository: **Nuvora**.
> Status: UI/architecture discovery phase. Online music providers are intentionally undecided until the owner chooses them.

## 1. Existing MuPlayer audit

Reviewed the current repository's `MainActivity.kt`, `MuPlayerApp.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `MainScreen.kt`, `HomeScreen.kt`, `LibraryScreen.kt`, `NowPlayingScreen.kt`, `PlaylistsScreen.kt`, `Theme.kt`, and `MusicViewModel.kt`.

### What is worth retaining
- Kotlin + Jetpack Compose + Material 3 foundation.
- AndroidX Media3 ExoPlayer and MediaSessionService for playback.
- Local audio permission flow and device library.
- Home/library/folder/playlist navigation, mini-player and full player concepts.
- Favorites/recent plays/play counts, theme palette, Coil artwork loading.
- Existing app's offline-first/privacy-friendly behavior.

### UI risks / improvement opportunities
- Existing screen files are large; use smaller reusable composables and clear screen/state boundaries.
- Make glass styling a consistent design system, not separate one-off effects.
- Prioritize readable contrast and fast scrolling over heavy blur/continuous animation, especially on low-memory devices.
- Separate provider-specific search/catalogue UI from the playback engine so sources can be chosen later.
- Every visible control should work; avoid finished-looking placeholder controls.
- Design deliberate loading, error, empty, permission-denied, offline and no-results states.

## 2. Open-source reference review

Reviewed Rhythm: https://github.com/cromaguy/Rhythm
- Kotlin, Jetpack Compose, Material 3 Expressive, Media3/ExoPlayer and local-first library.
- UX ideas worth studying: consistent shapes, library browsing, queue management, lyrics, responsive layouts, and separation of local playback from optional server streaming.
- Rhythm is GPL-3.0. Do not copy its source or distinctive screen implementations unless Nuvora intentionally adopts compatible licensing and preserves required notices. For now, use high-level UX lessons only and implement an original design.

## 3. Proposed original MuPlayer visual system

### Art direction
- Dark navy foundation with restrained electric-blue/cyan accents.
- Selective glass-like cards, with solid fallback surfaces for accessibility and performance.
- Album artwork adds visual interest; typography and controls stay calm and legible.
- Consistent rounded shapes, subtle elevation, short transitions, no constant heavy animations.

### Navigation
- Home; Search (provider-neutral until sources are chosen); Library; Playlists; Settings.
- Persistent mini-player above navigation when a track is active. Full-screen Now Playing opens from it.

### Screen requirements
- **Home:** search entry, recently played, favorites/mixes, and a clear local/online distinction once sources are selected.
- **Search:** only owner-approved provider filters; loading/error/empty states; results show artwork, title, artist, source attribution and supported actions.
- **Library:** Songs, Albums, Artists, Folders and Favorites; sort/search; efficient lazy lists and artwork placeholders.
- **Now Playing:** artwork, title/artist, seek bar, time, previous/play-pause/next, shuffle/repeat, queue, lyrics entry and supported output/volume controls.
- **Queue:** reorder, remove and clear with confirmation; show source for streamed tracks.
- **Playlists:** create, rename, add/remove tracks, reorder and persist locally.
- **Settings:** theme/accent, playback, audio, library scan, privacy, source settings, about/licenses.
- **Lyrics:** timed lyrics where permitted, manual/local fallback and graceful no-lyrics state.

## 4. Architecture direction

- `ui/`: Compose screens, components, design tokens and navigation.
- `playback/`: Media3 player/session integration and source-agnostic playback queue.
- `library/`: local MediaStore scanning and local metadata.
- `providers/`: interfaces for search/catalogue/stream resolution. Implement only sources explicitly selected by the owner and permitted by their terms.
- `data/`: Room/DataStore persistence for playlists, favorites, settings and recent activity.
- `domain/`: shared track/source models and use cases.
- `tests/`: ViewModel/repository tests and UI smoke tests.
- GitHub Actions: build/test checks; APK release workflow later.

Keep local-file playback usable without internet. Provider failures must not break the local library.

## 5. Streaming-source decision is pending

No online provider has been implemented or selected yet. Before adding a source, confirm official/authorized access, playback and attribution rules, account/auth requirements, ads, caching/download and background-play rules, and secret handling.

YouTube's developer policies prohibit extracting/separating audio from YouTube videos and prohibit background playback through YouTube API services. Do not implement YouTube-to-MP3 extraction or an unofficial bypass. If selected, use a compliant playback path and required player/attribution behavior.

## 6. Implementation order

1. Finalize UI direction and source choices with the owner.
2. Establish a buildable Android Gradle project in Nuvora without touching original MuPlayer.
3. Implement design tokens, navigation, Home, Library and mini-player shell.
4. Port/rebuild local playback with explicit package/app-ID decisions.
5. Add full player, queue, playlists, lyrics and settings.
6. Add only approved online provider adapters.
7. Run Gradle build/tests, fix regressions, then publish a test APK through GitHub Actions.

## 7. Acceptance criteria

- Nuvora is separate from the original repository.
- Launcher/display name is MuPlayer.
- Local music remains usable offline.
- One consistent, accessible design system across screens.
- Online providers are configurable and source attribution is visible.
- No unsupported scraping, audio extraction, or terms-bypassing playback.
- No secrets committed to source control.
- CI builds the debug APK and tests pass before release.
