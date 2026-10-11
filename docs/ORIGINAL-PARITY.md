# Original-player analysis and Android repairs

The user approved packaging after browser review. Current 0.2.3 test APK passes the focused native local-playback/mode-icon, synthetic HTTPS connection and layout suites. Existing 0.2.2 evidence is historical only; physical-device behaviour is not certified. Original NaviGlass was inspected and served read-only; no original files, music files or server configuration were modified.

## Source paths traced

Original public/index.html and styles.css define WebGL artwork, top seek strip, caption/reflection, transport/browse slider, six-item bottom bar, drop-ups and the song drawer. public/app.js owns browse state, adaptive chrome sizing, drawer/action placement, radio search and Subsonic authentication. public/renderer.js owns cover geometry, clipping, projected bounds and transitions. The supporting drawer-navigation.js, context-menu-position.js, playback-order.js, audio-seek.js, navidrome-playback.js and server.mjs were traced for the relevant selection, queue, placement and network paths. This is not a claim that every desktop feature has been ported.

| Original behaviour | Android repair |
| --- | --- |
| Touch portrait drawer: full width, bottom anchored; touch landscape: 70% width, capped at 500px, centred | Dedicated orientation-aware song drawer with bottom slide animation in portrait, viewport bounds and independent scroll |
| Song row three-dot menu: Add to playlist, Favourite, More info | Every song list has actions; a top-layer popover avoids clipping by the scrolling drawer, dismisses safely and supports details/return |
| fitControlsLayout adjusts transport/nav shell to screen space | Cover-width top strip, independently fitted bottom shell, square buttons, proportional icons and separated navigation underline |
| Immersive mode removes bottom browse/menu chrome | Floating previous/play/next pill remains; exit restores the normal controls |
| Now-playing caption shows song title and album/year | Single-song and playing-album captions show album name and year separated by a vertical bar, with ellipsis |
| Radio station-name search | One debounced search box or Enter, paged results, starter reset and visible loading/error feedback; search field remains visible |
| Songs/Artists/Playlist choose entries for the persistent renderer | All Songs, artists and playlists browse playable track-cover entries, not opaque replacement pages |
| Horizontal move gestures distinguish tap/swipe | Exclusive canvas touch handling, pointer capture, thresholds and cancel-safe state; actual native swipes tested |
| Radio supports valid HLS streams and directory failover | Native Media3 HLS, HTTPS validation, official mirror discovery/failover |
| Original homepage uses /navidrome proxy and compatible authentication | Same-host proxy discovery, HTTPS-only encoded-auth fallback and specific connection errors; publicly reachable HTTPS does not require a tailnet client |
| Albums/Songs/Artists/Playlist three-option sorts | A–Z/year ascending/year descending on each browse menu; persisted selection and correctly reordered cover entries |
| Three animated bars for a playing row, paused triangle | Original marker CSS and animation reused without rebuilding nodes on the playback ticker; reduced-motion support |
| Five-second idle return to playing entry | Returns within the current collection; paused playback and open layers suppress it |
| Original playback-order decisions | Original JS policy reused in preview; repeat Off → All → Song, real shuffle, manual skips and automatic adjacent-album continuation; native lazy adjacent queue source and JVM policy tests |
| Keyboard and wheel navigation | Cover arrows, Space, Enter, drawer Up/Down/Enter/Escape and accumulated wheel gestures, without stealing input-field keys |
| Radio artwork, saved stations, extended track info | Provider art/fallback, device-local station saves and detailed on-demand native song metadata; absent fields are not invented |

## Layout evidence

The source touch CSS was traced directly instead of applying desktop cover-width assumptions to phones. Historical 0.2.2 measurements at 850×412 CSS pixels: artwork is 238.96px square, bottom shell 430.28×69.59px and caption ends at 326.08px before controls start at 334.41px. The usual short landscape cover ratio is 58%; exceptionally short native windows reserve 164px for top positioning, a readable caption and square controls. This fixed a measured 2.44px overlap at 863×360 without compressing the buttons or making the caption unreadably small.

Portrait drawer width equals the WebView viewport and its bottom is flush. Landscape width is min(70% viewport width, 500px, viewport width minus 64px). Native rotation, Android MotionEvents, square buttons, label bounds, action-menu bounds and More info/return are tested. Original SVG icons, outline heart path, perspective and reflection renderer are reused.

Source and Android were captured side by side at the same CSS dimensions, density, centre artwork and paused fixture metadata. Original touch media rules were enabled in the served comparison response only; original disk files were not edited. Real browser and Android touch input were tested separately. See design-qa.md for comparison history and explicit platform differences.

Current 844×390 review: artwork is 226px square; caption ends at 315.88px before controls start at 319.77px. Normal controls are 409.70×62.23px; title/album text measures 27.44/17.64px. Larger landscape captions, a dark browse thumb and smaller immersive buttons are intentional requested refinements. Full-view control is hidden in portrait. Native status-bar hiding cannot be established by browser screenshots. Eight matched source/implementation states, including animated playing rows, were reviewed.

## Connection outcome and limits

Earlier read-only personal-server checks reached the original proxy but received Subsonic code 40 under both authentication methods. This UI update does not claim to solve those account credentials. The direct approved private route was unavailable from the development host. A later read-only check reached the public HTTPS homepage and its Navidrome proxy without Tailscale; missing-username API feedback establishes route reachability, not successful account login. Only direct private routes need tailnet access. No private URL, username or password is published in this report or source. No server deployment/restart occurred.

Advanced shelves/year/genre/composer options, server playlist editing, radio saving to Navidrome, detailed equalizer/settings and every desktop playback/menu option are not fully ported. The playback-order decisions relevant to this review are implemented, not full desktop feature parity. Native Media3 replaces browser audio. Remote playback now negotiates Android-compatible streams, and server favourites/catalogs/radio lists are imported; see NAVIDROME-REVIEW.md. Android source access, encrypted credentials, 44px drawer action hit areas, readable landscape list fonts and native OS insets are intentional platform adaptations. Physical-device, Bluetooth, battery and full text-scale/accessibility testing remain necessary.
