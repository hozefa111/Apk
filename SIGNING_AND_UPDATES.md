# Orbit Player signed updates

Orbit Player uses a private signing key for long-term updates. Android requires the *same signing certificate* for each in-place update, plus a higher versionCode.

## Set up once

1. Keep the signing-key backup and password in a safe private location. Never commit either to this public repository.
2. Open [GitHub Actions secrets](https://github.com/hozefa111/Apk/settings/secrets/actions).
3. Add `ORBIT_KEYSTORE_B64`: the one-line base64 contents of the private `orbit-player-release.p12` keystore (provided separately to the app owner).
4. Add `ORBIT_SIGN_PASSWORD`: the password for that keystore (also provided separately).
5. Open [Publish Signed APK](https://github.com/hozefa111/Apk/actions/workflows/release-apk.yml), choose Run workflow on `main`.
6. After the workflow publishes a signed APK to [GitHub Releases](https://github.com/hozefa111/Apk/releases/latest), download and install it. You need to uninstall your **old debug-signed** Orbit Player once because its signing key differs. This may remove locally saved playback progress; your video files are unaffected.
7. Future signed releases use the same key, so Android can install the newer APK *over* the current app without uninstalling it. Press `Updates` in the app to check the latest published release, download the APK, and approve installation.

Android never permits an ordinary third-party app to silently install updates without user approval. The Updates screen is explicitly user-initiated and contacts only GitHub releases, not analytics services.

## Build behavior

- `build-apk.yml` produces a debug APK for development and unit tests; that debug build is **not update-signature-stable** between clean GitHub runners.
- `release-apk.yml` produces a release-signed APK **only when both private GitHub Actions secrets exist**. Otherwise, it exits without publishing.
- The release workflow verifies the APK signing, attaches an APK and a SHA256SUMS file, and creates or updates a GitHub Release for the current versionName.
- To publish the next release, increase *both* `versionCode` and `versionName` in `app/build.gradle.kts`. Keep the keystore unchanged.

Never place signing passwords, keystores, base64 secrets, or signing backups in Git commits or public build artifacts.
