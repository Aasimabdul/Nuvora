# Nuvora

Nuvora is an Android online music discovery and streaming app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Current implementation

- Live track search and popular-track discovery through the Jamendo API.
- Full track playback through Media3 ExoPlayer.
- Play/pause and next-track controls.
- Track metadata and artwork URLs from the catalogue.
- Jamendo client ID entry in app settings, saved locally on the device.
- Dark navy UI with electric-blue and cyan accents.

**Setup required:** create your own application at https://devportal.jamendo.com/ and enter its client ID in Nuvora's settings. Until a valid client ID is entered, the app cannot fetch real tracks. Do not commit private credentials to the repository.

## Provider and licensing

This first integration uses Jamendo's catalogue of independent music. Jamendo's API documentation requires a registered client ID. Its API terms state that free use is for non-commercial projects; commercial use requires contacting Jamendo. Track licences vary, so preserve appropriate attribution and check each track's licence before redistributing or using it in any commercial context.

- API docs: https://developer.jamendo.com/v3.0/docs
- Authentication: https://developer.jamendo.com/v3.0/authentication
- API terms: https://devportal.jamendo.com/api_terms_of_use

This integration does not unlock commercial-label catalogues such as Spotify or YouTube Music. It also does not bypass any provider's access controls.

## Current limitations

- Background playback and media notification/service are not implemented yet.
- Favourites, playlists, queue persistence, and account sync are not implemented yet.
- This is an early streaming implementation and needs device testing before a release.

## Build

The project uses Kotlin, Jetpack Compose, Material 3, and AndroidX Media3. GitHub Actions builds a debug APK on pushes and pull requests.
