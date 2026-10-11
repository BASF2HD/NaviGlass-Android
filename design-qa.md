# Design QA — NaviGlass 0.2.4

final result: passed

Scope: requested shuffle/repeat states, search ranking, landscape chrome and speaker popover, plus regression review of the existing drawers and navigation. This is scoped visual/interaction QA, not a claim of complete desktop parity or physical-device certification.

## Evidence

The screenshot-to-code workflow used the original player's code and the supplied portrait/landscape references. The original repository was read-only for comparison. Sandbox-isolated Electron rendered the source and Android implementation at identical 390×844 and 844×390 CSS viewports. Ten full side-by-side states were inspected, with focused control/drawer crops: player, drawer, actions, immersive and playing rows, plus vertical volume in both orientations. Browser tooling here is an isolated Chromium/Electron renderer, not control of the Codex in-app browser. Private reference artwork stays in temporary QA files; published screenshots use invented demo albums only.

## Repairs and five fidelity surfaces

- Typography: original system stack and bounded song/album hierarchy remain. Search returns title-prefix matches first, then title-substring and artist/album matches; its hint explains this ordering. All letters A–Z pass browser fixtures and actual native SQLite tests, including literal wildcard escaping.
- Spacing/layout: portrait control dimensions are unchanged. At 844×390, landscape seek width is 573.91px versus the 226px centre cover; controls are 59.48px tall and Play is 23.48px square. The browse thumb is 22×10px in landscape only. Wider seek and larger captions intentionally follow the user's requests rather than matching the source's narrower strip. Caption and controls remain separated.
- Colours/tokens: disabled fixed-stroke shuffle/repeat SVG images now use .45 opacity; enabled images use full opacity and a subtle cyan highlight. Repeat All has no 1 mark; Repeat Song has exactly one mark. Native tests check computed brightness and real playback modes, not merely class names.
- Assets: existing original SVG control paths, Cover Flow logo and reflection renderer are reused. No replacement logo/artwork system was introduced. Screenshot artwork is synthetic; no account or personal music is shown.
- Content/interaction: a narrow vertical volume slider appears below the speaker, aligned to its right edge and clamped to the viewport. It is a light-dismiss top-layer popover, not a modal horizontal window. Native volume changes, Escape dismissal and orientation positioning pass. Landscape popover is smaller than portrait, matching limited height.

## Verification

Build, lint (no errors) and nine JVM tests pass. Three focused emulator suites pass: PlaybackReviewIntegrationTest, LayoutIntegrationTest and ServerIntegrationTest. They cover actual local album-boundary playback, mode states and brightness, native volume, A–Z search ranking, native swipes/rotation/drawers, and synthetic HTTPS login/raw streaming/encrypted credential restoration. Browser regression checks cover keyboard, mouse, repeat/shuffle queues, automatic adjacent albums, idle return, playing bars, search, sorting, saved radio and detailed information.

No actionable P0/P1/P2 finding remains in these reviewed states. Remaining P3/device checks: real handset insets and hit areas, font scaling, Bluetooth/battery behaviour, Recent Apps dismissal, large libraries, personal-server login and remote multi-album continuity. The browser simulates local playback and does not send Navidrome login requests. No personal server, original player runtime or music file was modified. APK publication was explicitly approved after UI review.
