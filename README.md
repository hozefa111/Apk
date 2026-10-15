# Orbit Player Lite — Android Offline Video Player

**Status: source code prepared, APK not compiled or device-tested in the authoring environment.**

Orbit Player Lite is a small, ad-free, offline Android video player written in Kotlin and using AndroidX Media3 ExoPlayer. This is an initial implementation, **not a finished MX Player equivalent**. There is no LibVLC/FFmpeg software decoder fallback in this version.

## Implemented in the source

- MediaStore video library with alphabetical/newest ordering, folder filter, search, asynchronous thumbnails.
- Android Storage Access Framework video picker (works even when library permission is denied).
- Android 13+ video library permission and Android 14+ selected-video permission handling.
- ExoPlayer hardware/media-device-supported video playback with built-in seek bar, pause and play.
- Playback resume position saved on lifecycle transitions and every 5 seconds.
- Playback speed, audio track selection, internal subtitle track selection.
- Manual SRT / ASS / SSA / VTT subtitle picker (where ExoPlayer supports the format).
- Aspect fit, crop and stretch, screen rotation, repeat video.
- Double tap ±10 seconds and swipe brightness (left) or volume (right) in edge gesture regions.
- Previous/next video when opened from the in-app library in the same process.
- No advertisements, network permissions, accounts, or analytics.
- GitHub Actions workflow that builds and uploads a **debug-signed, installable APK**.

## Important limitations

- **Not all video/audio codecs work.** Playback depends on Android system decoders and Media3's demuxers. Some AVI, VC-1, DTS, E-AC-3, AC-3, older codecs, or unusual video profile files might not play or might have no sound. MKV extension alone is not a guarantee of playback.
- No LibVLC engine fallback, picture-in-picture, background playback, playlists, or automatic external subtitle search yet.
- Online streaming is deliberately not included; app has no internet permission.
- APK size cannot be established without a successful build.
- API 26+ (Android 8+) is configured; actual target devices are untested.
- Content URI picker and media permissions are OEM/Android-version dependent and should be tested on physical devices.
- Debug APK is signed with the Android debug keystore, not a personal production release signing key.
- User-contributed media files remain unchanged; the app only reads them.

## Easiest way to obtain APK (GitHub Actions)

1. Create a new GitHub repository (private is fine) and upload **all files and folders** from this project, including `.github/workflows/build-apk.yml` (the `.github` folder must not be omitted).
2. Set the default branch to `main`, or push to `main`/`master`.
3. Open the repository → **Actions** → **Build Android APK** → **Run workflow**.
4. After the run succeeds, click the workflow run and download `OrbitPlayer-Lite-APK` under **Artifacts**.
5. Extract the downloaded ZIP to get `app-debug.apk`. Copy it to your Android phone and install it. Android may ask you to permit installs from that source.

The Action must be enabled in repository settings. If the build fails, open its logs: the APK **was not produced**, and failures need resolving before installation.

## Alternative: Android Studio

1. Install Android Studio with Android SDK Platform 36 and SDK Build Tools 36.x, and use JDK 17.
2. Open the **OrbitPlayerAndroid** folder as a Gradle Android project. Use Gradle **8.13** (select a local Gradle 8.13 distribution if Android Studio asks; this project intentionally does not include the official Gradle wrapper binary).
3. Allow it to download plugin and AndroidX dependencies.
4. Select **Build → Build APK(s)** (debug).
5. Result: `app/build/outputs/apk/debug/app-debug.apk`.

CLI with Gradle 8.13 on PATH: `gradle :app:assembleDebug :app:testDebugUnitTest`.

## Technology versions

- Kotlin 2.2.20
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- compile SDK 36, target 35, min 26
- AndroidX Media3 1.11.1
- RecyclerView 1.4.0
- Java 17

## Privacy & permissions

- `READ_MEDIA_VIDEO` (Android 13+): optional library listing.
- `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+): partial video access.
- `READ_EXTERNAL_STORAGE` (Android 12 and earlier): optional library listing.
- `ACTION_OPEN_DOCUMENT`: system video picker without library permission.
- No `INTERNET` permission and no tracking SDKs.

## Source tree

`MainActivity` and `VideoRepository` provide the local video library. `VideoAdapter` generates/cache thumbnails. `PlayerActivity` handles video playback, tracks, subtitle picking, gestures, and resume. `PlaybackQueue` supports list navigation within the running app. `PlayerGestureZone` manages touch events. More details are in `BUILD_NOTES.md`.

## Required pre-release checks

Complete a successful GitHub Actions or local build; test MP4/H.264/AAC, MKV/H.265, external .srt, internal subtitles, rotation, seek, volume and brightness gestures, app background/resume, library permission partial/full/denied, and file picker on at least one Android device. Fix reported problems and run a code/security review before publishing.
