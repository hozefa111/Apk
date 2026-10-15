# Build notes and engineering decisions

## Build status

This workspace does not have the Android SDK, Gradle, Gradle wrapper jar or access to Maven/Google downloads. There is **no compiled APK and no Android-device test result** attached to this source delivery. GitHub Actions is set up to perform the actual build on an online Ubuntu runner. Build failures remain possible and should be investigated using workflow logs.

## Design goals

- Code is separated into browsing (`MainActivity` / `VideoRepository`), list rendering (`VideoAdapter`), playback (`PlayerActivity`), and gesture handling (`PlayerGestureZone`).
- ExoPlayer and native Android views avoid additional native codec libraries, reducing initial package weight.
- The Android platform's Storage Access Framework provides a no-permission fallback for single-file playback.
- The player stops and releases its media session on `onStop`. Background audio is **not** part of this version.
- Resume history is stored privately in Android SharedPreferences keyed by a URI hash; no sensitive file content is uploaded.

## Potential improvements for next iterations

1. Run official compilation and instrumented-device tests. Correct any API or OEM-specific issues.
2. Use a proper PlayerEngine abstraction before introducing LibVLC. Size-test ARM64 native libraries against codec coverage.
3. Migrate deprecated activity result APIs to Activity Result Contracts after baseline passes.
4. Add full lifecycle/media-session support for PiP and background playback.
5. Add track subtitle sync, external subtitle filename matching via SAF-granted tree access, and playback queue persisted on disk.
6. Add robust thumbnail request cancellation/de-duplication for huge libraries and memory-pressure testing.
7. Add UI automation tests and accessibility audits for touch targets, screen-reader labels, landscape layouts, and insets.
8. Configure private release signing and run R8 release builds before distribution.

## Official dependency references

- Media3: https://developer.android.com/jetpack/androidx/releases/media3
- AGP 8.13: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Android partial media permission: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
