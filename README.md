# Nuvora

Nuvora is an Android music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Features

- Search and discover tracks from the Audius public catalogue.
- Stream tracks using Media3 ExoPlayer.
- Save favourites during the current app session.
- Choose from Neon Blue, AMOLED Black, Purple Night, Forest, and Light themes.
- Configure an Audius-compatible HTTPS API base URL in Settings.
- Start a track download from the download icon; Android Download Manager shows progress and saves files under Music/Nuvora.

## Music source

The default API endpoint is `https://api.audius.co/v1`. In the app, open **Settings → Music source**, enter an HTTPS base URL for an Audius-compatible API, and save it. The endpoint must provide the expected tracks search response and stream routes.

## Downloads and rights

Downloads depend on the provider's response, availability, and applicable track permissions. Only download tracks when you have permission to do so. A download request may fail if the provider does not permit the stream to be saved as a file.

## Build

The project uses Gradle and Java 17. To build a debug APK locally:

```bash
gradle --no-daemon assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and attaches `Nuvora-debug.apk` to releases.

## Testing note

This is an early release. Verify search, playback, theme persistence, and downloads on a real Android device before relying on it. Provider availability and playback can vary by track and network.
