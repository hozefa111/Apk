# 3 Patti PRO Android (bundled edition 2.0)
This is an isolated Android application derived from the original [3 Patti PRO web game](https://github.com/hozefa111/hh), which is **not changed** by this build.

**Firebase**: The Android APK uses the same original unmodified `firebase-config.js`, same account login and `patti-pro-a61d8` Firestore. No Firebase migration, reset, data clearing or database replacement.

**Full UI**: The GitHub Actions workflow checks out a pinned read-only snapshot of the HH website, copies its HTML/CSS/JS/manifest/icons to the Android APK as packaged local assets, disables only service-worker registration in the Android COPY, and builds a signed debug APK. This fixes dependencies on the original GitHub Pages web address for loading the interface.

**Features**:
- Android launcher icon and splash screen
- Bundled site and real Firebase login/data from the existing project (internet still needed)
- Native Android share sheet for scorecard/ranking images
- Native Android back control wired to site's `handleAndroidBack()`
- Connection error screen with tap-to-retry

**Compatibility**: Android 8.0+; install APK from workflow artifact after extracting ZIP. APK package `com.hozefa.threepattipro.stable` (different from prototype app, can be installed beside it). This is a debug-signed, not Play-Store-signed APK. WebView Android runtime and Firebase account access still require on-device testing.

**Source protection**: Neither the original `hozefa111/hh` repository nor Firebase server data is written to by any build step. All changes are on `hozefa111/Apk` branch `three-patti-pro-android`.
