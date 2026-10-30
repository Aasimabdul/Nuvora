# Nuvora

Nuvora is an Android music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Features

- Search and discover tracks from the Audius public catalogue for in-app playback.
- Choose Audius, YouTube Music, JioSaavn, Spotify, or SoundCloud from the source selector.
- YouTube Music, JioSaavn, Spotify, and SoundCloud searches hand off to the official app when Android supports the link, or to the service website.
- Stream tracks using Media3 ExoPlayer.
- Save favourites during the current app session.
- Choose from Neon Blue, AMOLED Black, Purple Night, Forest, and Light themes.
- Configure an Audius-compatible HTTPS API base URL in Settings.
- Start a track download from the download icon; Android Download Manager shows progress and saves files under Music/Nuvora.

## Music source

The default in-app playback provider is Audius, using `https://api.audius.co/v1`. The other listed providers are opened through their official search links; they are not streamed inside Nuvora. In the app, open **Settings → Music source**, enter an HTTPS base URL for an Audius-compatible API, and save it. The endpoint must provide the expected tracks search response and stream routes.

## Provider support

| Provider | In-app playback | Current behavior |
| --- | --- | --- |
| Audius | **Supported** | Search and stream eligible tracks with Media3 through the Audius public API. |
| YouTube Music | Not supported | Search opens the official YouTube Music app/site. Nuvora does not extract audio or bypass YouTube playback rules. |
| JioSaavn | Not configured | Search opens the official JioSaavn service. No authorized direct-stream API integration is included. |
| Spotify | Not supported in Media3 | Search opens Spotify. Spotify's Android App Remote can control the official Spotify app, but does not provide Spotify audio streams to Nuvora. |
| SoundCloud | Not configured | Search opens SoundCloud. In-app playback would require registered API access, authorized playback, and required attribution. |

Only Audius is an in-app streaming provider in this public release. The selector describes this distinction in Settings, and selecting an external-only provider clears Audius search results so they are not mistaken for that provider's catalogue. Additional providers will only be integrated when their official developer access permits the intended playback experience.


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
