# Validation

## Current 0.2.5 Android test build — 11 October 2026

NaviGlass-Mobile hosts Android now; iOS is planned next, not built. Generic HTTPS-only APK, versionCode 8, unchanged application ID and debug signer. No private endpoint or account is preloaded.

- assembleDebug, lintDebug (no errors), nine JVM tests and assembleDebugAndroidTest pass.
- Three isolated emulator suites pass: PlaybackReviewIntegrationTest, LayoutIntegrationTest and ServerIntegrationTest, 20.152 seconds on the final rebuilt APK. The latter two now check strict title-prefix search including unrelated remote candidates, actual native speaker taps twice, outside drawer dismissal without cover click-through, 14px landscape end caps, and landscape Full View → portrait restoration including the native immersive flag. Existing playback/modes/volume and synthetic HTTPS/raw-stream/encrypted-login checks remain.
- Sandbox-isolated Mac Electron checks use real mouse input, not only DOM click calls: speaker toggles, outside drawer/context dismissal, A–Z prefix-only results, R/no-match/artist-only/substring queries, landscape arrow heights and Full View rotation pass without console errors.
- Broader browser playback/keyboard/automatic album-boundary/repeat/shuffle/idle-return/playing-marker tests also pass for 0.2.5. Sorting/persistence/radio/artwork/metadata/short-landscape checks pass after one retry for live broadcaster artwork. This is not a broadcaster-availability guarantee.
- Refreshed published screenshots contain only synthetic albums. Historical 0.2.4 matched-source comparisons are retained below, not claimed as newly rerun in full.
- Navidrome prefix search filters the first 200 server candidates; it may miss later matching titles in very large search result sets. Physical phones, personal-account login, battery/Bluetooth and manufacturer Recent Apps handling remain unverified.


## Historical 0.2.4 public test release — 11 October 2026

Generic HTTPS-only APK, versionCode 7, app label NaviGlass and unchanged application ID/debug signer. No credentials/private endpoint are preloaded. Public release and repository visibility were explicitly approved.

- assembleDebug, lintDebug (no errors) and nine JVM unit tests pass.
- Three focused emulator tests pass: PlaybackReviewIntegrationTest, LayoutIntegrationTest, ServerIntegrationTest. Actual OFF/ON image opacity, native shuffle/repeat modes, native volume, local adjacent-album playback, background lifecycle, portrait/landscape touch/layout and all-letter SQLite search ranking are checked. Title ranking occurs before the 200-result limit; literal wildcard queries are escaped. HTTPS fixture authentication, proxy discovery, encrypted credentials and raw audio streaming pass.
- Sandbox browser regression passes for all-letter prefix ranking, substring/album search, keyboard, playback queues, anchored vertical volume and landscape dimensions. Original-source comparisons cover ten matched states; only synthetic artwork is published.
- Generic APK network policy disallows cleartext traffic. Signature and version are checked; release includes a SHA-256 checksum.
- Physical-device behaviour, personal-account login, remote album continuity, background/task-dismiss behaviour on manufacturer builds and battery/Bluetooth still need tester feedback. The emulator was shut down after tests.


## Historical 0.2.3 test APK — 11 October 2026

User approved APK packaging after the Mac preview review. NaviGlass-Android-0.2.3-test.apk is now in the ignored builds/ folder; versionCode 6, app label NaviGlass, same application ID and debug certificate as 0.2.2. Older APKs remain untouched. No release or GitHub push was requested or performed.

- assembleDebug, lintDebug (no errors; existing warnings remain) and nine JVM tests pass.
- Three focused isolated-emulator suites pass: PlaybackReviewIntegrationTest, ServerIntegrationTest, LayoutIntegrationTest. Local synthetic audio advances manually to the next/prior album and automatically at album end. Shuffle toggles the native mode and highlight; repeat Off/All/Song changes the real mode, accessibility status and song-repeat glyph. Background lifecycle playback remains active. HTTPS fixture login, proxy discovery, authentication fallback, encrypted restoration, artwork and raw streaming pass; orientation/swipe/drawer/action tests pass.
- The initial smoke test failed because the bundled Media3 default connection result granted read-only commands even to the own-package controller. Production now explicitly grants standard player/session commands only after the existing own-package/trusted-controller gate. Authorised media-ID validation and same-package adjacent-album command validation remain in place. Rebuilt tests pass.
- Browser connection messaging now explicitly says no login request was sent: it is an unconnected UI preview, not a reported network failure. Native HTTPS login is implemented; browser login would need its own connection backend. A public HTTPS server does not require Tailscale.
- Verified APK signing; no personal name in the signing certificate. SHA-256: 5cde1685eb84c0b89f65a36fdab941457f18fa357284aa93b105bd57c427fc02. The exact previously approved HTTP host/port exception is preserved locally, not published in source/docs. No account credentials are bundled.
- Physical-phone Recent Apps dismissal/system bars, personal-server account login, battery/Bluetooth and remote multi-album continuity still need device testing. The server suite uses a synthetic fixture, not the user's account.

### Historical preview stage

The preview-only notes below describe the stage before APK approval; packaging is no longer paused.

## Historical Mac preview review — packaging was paused

No new APK was built, installed or copied for this review. The existing 0.2.2 APK is unchanged and does not contain the latest source changes. No personal music library or original-player server was changed.

- Java compilation and nine JVM unit tests pass: four existing scanner tests and five new adjacent-album/repeat/shuffle decision tests. This is not a new native integration run.
- Browser interaction checks pass: real keyboard Left/Right/Space/Enter input; drawer Up/Down/Enter/Escape; both mouse-wheel directions; mode buttons before playback; repeat Off/All/Song; shuffle changes order and previous retraces it; manual adjacent albums in both directions; automatic next album; repeat-all wraps; repeat-song restarts; five-second idle return; original animated playing bars and paused triangle.
- Live substring search retains the input and filters song/artist/album, including matches not starting with the query and no-match results.
- All four browse menus expose three sorting choices; sorting and saved radio stations persist across reload. Expanded song/station metadata, real station artwork, save/remove, Saved stations Cover Flow, and live BBC directory results were checked. One single-mirror run failed; the two-mirror timeout/failover repair passed the full suite afterwards. This is not a station-availability guarantee.
- Matching source/implementation screenshots reviewed at 390×844 and 844×390 CSS pixels, with focused controls/actions/playing-row crops. Short landscape 863×360 has no caption/control collision. No JavaScript console errors in passing suites. See ../design-qa.md.
- Browser assets serve the NaviGlass favicon/touch icon; Settings uses the same logo and product name. Preview is loopback-only. Local-song playback is a simulation; radio is live.
- A read-only check reached the user's public HTTPS homepage and same-host Navidrome proxy without a Tailscale client. The API responded with a missing-username error, proving route reachability only, not account authentication. No credentials were retried or published.
- Native source now auto-hides the status bar in normal landscape, fetches only adjacent album queues at boundaries, and stops playback when dismissed from Recent Apps. These changes compile but need native/device tests after UI approval. Background locking/Home should still retain audio.

## Historical 0.2.2 build verification

The following results describe the earlier APK only. They are not certification of the current modified source.



- Debug APK/test APK build successfully; lint completes without errors; four JVM unit tests pass.
- Rendered browser UI at 412×850, 850×412 and short landscape 863×360: All Songs/artist cover entries, drop-up placement, full-width bottom portrait drawer/wider centred landscape drawer, square playback buttons, unclipped three-dot menus on every song list, favourite persistence, More info/return, actual browser touchscreen events, browse slider, caption clearance, immersive floating transport, single-box live BBC results, no horizontal overflow and no JavaScript console errors.
- Three Android emulator integration suites pass: LayoutIntegrationTest, PlayerIntegrationTest and ServerIntegrationTest.
- LayoutIntegrationTest rotates the real native WebView, injects Android MotionEvents to swipe covers, checks landscape strip/control dimensions, per-label fit without touching neighbouring buttons, drawer width/overflow, upward menus, song covers and portrait restoration. It also checks the top-layer three-action menu, More info/return and square playback buttons. Screenshots use synthetic fixture data.
- PlayerIntegrationTest indexes synthetic embedded-cover audio, verifies playback, seek/next/shuffle/repeat, favourites/playlists, background playback, queue persistence and cancellation. Native UI searches the real directory for BBC; HLS stations are returned rather than filtered. Starter radio playback/background/next/pause and live-control disabling are tested.
- ServerIntegrationTest uses a pinned, isolated HTTPS fixture: root-URL 404 to original-player proxy auto-detection, salted token login, HTTPS encoded-password fallback, encrypted credential restoration, album/song/playlist/cover access, actual raw native audio streaming and disconnect. It checks the approved HTTP domain exception, wrong-port rejection and unrelated HTTP rejection.
- Final personal APK retains the exact HTTP exception previously approved by the user. Generic builds default to HTTPS-only. Private endpoint is passed as build properties, never committed.
- External radio is a live dependency: one intermediate run timed out waiting for BBC results; the retry and final full run passed. This is not an offline or universal station-availability guarantee.
- Live personal-server credentials were rejected with code 40 under both original login methods. Fixture success is not personal-account success. Direct Tailscale reachability from the development host was unavailable.

## Test-only HTTPS fixture

ServerIntegrationTest uses app/src/androidTest/assets/test-server.p12, excluded from Git and the player APK. Generate a short-lived PKCS12 fixture with CN=localhost and SAN localhost/127.0.0.1, alias fixture and test-only password fixture-only. The test trusts only that exact certificate and restores platform TLS defaults in finally. Production does not bypass certificate checks.

Run the tests only on a clean, isolated emulator with working internet:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Tests create/remove their own synthetic audio and database fixtures. Never run them against a personal library. Live directory/broadcaster tests depend on availability.

## Not certified

Physical phones, SD-card/provider variations, full handset text-scale accessibility, Bluetooth, battery/thermal behaviour, large real server libraries and every broadcaster remain unverified. No production-readiness, full desktop parity, universal format support or bit-perfect certification is claimed. The browser preview demonstrates UI; native credentials and HLS playback require the APK.
