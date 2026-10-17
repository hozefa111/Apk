# Orbit Player Pro — native Android video player

**Project:** Kotlin + AndroidX Media3 ExoPlayer • package `com.orbitplayer.app` • Android 8.0+ (API 26)

This is a **native APK**, not a website packaged in a WebView. All local video playback runs on the Android device. No advertising, login or media uploads.

## Releases and verification

- Latest previously published signed version: [v0.3.0](https://github.com/hozefa111/Apk/releases/tag/v0.3.0).
- The current source targets **v0.3.1**. Do not treat it as an installable signed release until a corresponding verified GitHub Release exists.
- The native build workflow compiles debug/release variants, checks resource/layout contracts and runs unit tests.
- The emulator smoke workflow starts the Android library, opens Settings via a real tap, plays generated H.264/AAC media from MediaStore, checks Player readiness and accessible controls, then rotates the screen.
- The signed-release workflow publishes only after the main-branch emulator test succeeds, or when an authorized maintainer explicitly dispatches a release.
- **Important:** CI/emulator tests cannot establish hardware codec support or smooth performance on every physical phone. Real-device checks are a required release quality gate.

## Current features

- Video library with folder/album thumbnails, folder counts, sort, pin/hide/unhide, gallery and list modes.
- Search by file/folder name, recent videos, watch history, continue watching.
- Favorites, ordered playlists and multi-select list management.
- Play All with next-video playback, repeat-one support, stable/persisted queues and recovery from unreadable files.
- Native full-screen playback with bottom transport/timeline, touch lock, pinch/gesture seeking, volume and brightness gestures.
- Playback speed, audio and subtitle tracks, external subtitle picker (SRT/ASS/SSA/VTT depending on Media3/device support), fit/crop/stretch and orientation.
- Picture-in-picture, foreground Android MediaSession support for wired/Bluetooth headset play/pause/next/previous controls, configurable settings, sleep timer and saved playback progress.
- Android 13+ video permission flow, partial selected-video permission on Android 14+, and Android file picker.
- Optional **user-initiated** GitHub update checks; other local playback is offline.

## Known limitations — not promised

- There is **no LibVLC/FFmpeg codec fallback**. Unsupported video/audio codecs can still fail or be silent.
- Subtitle timing offset and fine-grained typography controls, chapter selection, advanced filter syntax, **background** MediaSessionService/notification playback, NAS/SMB folders and import/export are not complete. Headset play/pause works while the player is active; background playback is not promised.
- Actual-device QA is outstanding. Tests should include Android 8, 13 and 14+, small and large libraries, rotation, PiP, subtitle timing, seek, queue transition, and corrupt media.
- The web version is not used at runtime. Design/function parity should be audited against the actual web source rather than assumed.

## Build locally

Requires Java 17, Gradle 8.13 and Android SDK 36.

```bash
python3 scripts/check_layouts.py
python3 scripts/check_library_ui.py
gradle --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Signed releases** use a private PKCS12 keystore via GitHub Actions secrets. Debug builds do not use the production signing identity and may not upgrade an installed signed APK. Preserve the app ID, signing identity and increasing versionCode for update compatibility. Never delete app data just to update.

## Privacy

Local files remain where they are. Favorite/playlist metadata and resume positions are stored locally. The internet permission exists only to support a user-triggered GitHub update check. The app does not have accounts or telemetry.

See [the release acceptance checklist](docs/ORBIT_PRO_MASTER_RELEASE_PLAN.md) for what still needs verification.
