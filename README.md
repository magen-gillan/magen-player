# magen player

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" />
</p>

<p align="center"><b>Local-first Android video player powered by libmpv.</b></p>

magen player is a customizable video player for Android with a Jetpack Compose interface. Media discovery, playback, playlists, and Shorts use files stored on the device. The application does not stream from websites or connect to remote media services.

## Features

- Local video playback powered by libmpv, with customizable gestures, frame navigation, zoom, and pan.
- Browse device folders, organize local playlists, and track watched videos.
- Shorts mode with sorting and a local Favorites folder.
- Glass-themed controls, configurable tabs, local subtitle files, and HDR-to-SDR tone mapping.
- No internet permission or runtime network requests in the app.

## Build

The Release variant is configured for `arm64-v8a` to keep the device-specific APK smaller. Build it from this source tree with:

```bash
./gradlew :app:assembleRelease
```

Gradle dependencies must already be available in the local cache for a fully offline build.

## Translations

Android translations are stored in `app/src/main/res/values*/strings.xml`. See `CONTRIBUTING.md` for local contribution workflows.

## Credits

magen player is built on libmpv and draws on ideas and code from mpvEx, mpv-android, mpvKt, Next Player, and Gramophone. Their respective licenses and notices are preserved with the source.

## License

Distributed under the Apache License 2.0. See `LICENSE` for details.
