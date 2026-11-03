# Nuvora Desktop

A separate Tauri 2 + React + TypeScript desktop application housed in the existing [Nuvora repository](https://github.com/Aasimabdul/Nuvora). Android sources under `/app` remain independent.

## Status

This is the initial desktop foundation. The first provider target is public Audius catalogue discovery and compatible public stream playback. A search result is not a guarantee that playback will be available. Spotify, YouTube Music and JioSaavn must use documented integrations or official-service handoff unless an authorized direct-playback API is available. No DRM bypass or protected stream extraction is included.

## Prerequisites

- Node.js 20 or newer and npm
- Rust stable toolchain
- Tauri 2 system dependencies for your operating system
- On Linux, the WebKitGTK/GTK development packages required by Tauri 2

## Development

```sh
cd desktop
npm ci
npm run dev
```

In a second terminal, from `desktop/`:

```sh
npm run tauri dev
```

## Checks and builds

```sh
npm ci
npm run check
npm test
npm run build
npm run tauri build
```

The Tauri bundle configuration targets Windows NSIS and Linux AppImage/DEB. Use platform-specific CI runners to create platform artifacts; an artifact is not considered verified until the corresponding CI build succeeds.

## Release assets

The repository's existing Android release workflow remains separate. The desktop workflow builds Windows and Linux packages on their native runners, then uploads the resulting EXE, AppImage and DEB files to the matching GitHub release for a version tag. A release upload only occurs after both platform build jobs succeed.

## Current limitations

- Desktop Audius playback and discovery must still be validated in CI and on real Windows/Linux machines.
- Other providers are not represented as playable unless a permitted stream source is available.
- System media keys, gapless playback, crossfade, equalizer DSP, updater signing and provider account login are not claimed as implemented.
- Windows and Linux artifacts cannot be runtime-tested on every OS from a single runner.
