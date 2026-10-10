# Third-party notices

## Three.js r128 / npm 0.128.0

Copyright 2010–2021 Three.js authors. MIT licence, preserved in `app/src/main/assets/vendor/three-LICENSE.txt` and the minified runtime header. Official project: https://github.com/mrdoob/three.js. The runtime is packaged locally; it is not loaded from a CDN.

## Android libraries

AndroidX Activity, Core, Lifecycle, WebKit, Media3 and AndroidX Test are obtained through their official Google Maven artifacts. Their supplied notices/licences remain applicable. Most AndroidX components use Apache License 2.0; see https://android.googlesource.com/platform/frameworks/support/ and https://github.com/androidx/media.

Transitive dependencies retain their own licences, including Guava and supporting annotation/lifecycle libraries. Build and test tooling has separate licences; no SDK, emulator, JDK, signing key or Gradle cache is distributed in this repository.

## Existing Cover Flow implementation

The renderer and geometry originate from the owner's existing NaviGlassPlayer project, adapted only in this separate Android project. The Android UI, icons, synthetic test artwork/tone and small tween engine were created for this project. GSAP is not included or required by this Android version.
