# Validation

Version: 0.1.0 testing build. Validation uses synthetic albums and audio only.

- Android debug APK compilation: passed.
- Android lint: no errors; remaining warnings include dependency-version notices, required JavaScript use for the bundled renderer, the application-context singleton and the exported media-session service. The service rejects untrusted external controllers and validates requested song IDs.
- JVM unit tests: four passed (audio extension filtering, malformed/unsupported suffixes, metadata/track-number parsing and stable IDs).
- Offscreen UI smoke tests: portrait/landscape, no horizontal overflow, album navigation, song selection/play-next simulation and idle renderer frame scheduling passed without JavaScript errors. These tests verify the synthetic browser preview, not native audio.
- Native Android 15 / API 35 emulator integration: passed with synthetic audio. Covers the native WebView bridge, library/song-list display, indexing, embedded artwork, favourites/playlists, actual Media3 playback progress, seeking, next/shuffle/repeat, activity-stopped background playback, saved queue settings and cancelled-scan rollback.
- Real-device listening and SD-card testing: not performed. Physical-device testing is required before production release.

No claim of bit-perfect output, measured battery savings or universal codec compatibility is made.
