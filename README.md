# Nuvora

Nuvora is an Android music app project focused on online music discovery and a clean, modern listening experience.

> **Current status:** UI prototype / foundation. The displayed tracks are sample content. No live catalogue provider or real streaming playback is connected yet; the music source will be selected before provider integration.

## Direction
- Online-first discovery: home feed, moods, trending tracks, search, artists, albums and playlists.
- Dark navy UI with electric-blue and cyan accents.
- Selective glass-like surfaces, with performance and readability prioritized.
- Source-neutral playback architecture so an approved provider can be added without redesigning the UI.
- Library, favourites, playlists, queue and background playback as implementation milestones.

## Build
The project uses Kotlin, Jetpack Compose, Material 3 and AndroidX Media3. GitHub Actions builds a debug APK on pushes and pull requests.

## Music source policy
The provider is intentionally undecided. Integration will be based on the owner's choice and the provider's official API/terms, including attribution, authentication, ads and background-play rules. No unofficial extraction or terms-bypassing playback will be added.

## Project identity
Nuvora is its own app and repository.

## Source switcher
The Home header includes a **Source** button with a picker for YouTube, JioSaavn, Spotify, SoundCloud and a future-sources option. At this stage it changes the selected UI source only; it does not connect to those services or enable live playback. Provider availability and permissions must be verified before implementing each integration.
