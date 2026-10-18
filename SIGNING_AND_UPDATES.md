# Orbit Player signed updates

Orbit Player uses a private signing key for long-term updates. Android requires the *same signing certificate* for each in-place update, plus a higher versionCode.

## Set up once

1. Keep the signing-key backup and password in a safe private location. Never commit either to this public repository.
2. Open [GitHub Actions secrets](https://github.com/hozefa111/Apk/settings/secrets/actions).
3. Add `ORBIT_KEYSTORE_B64`: the one-line base64 contents of the private `orbit-player-release.p12` keystore (provided separately to the app owner).
4. Add `ORBIT_SIGN_PASSWORD`: the password for that keystore (also provided separately).
5. Before publication, run the development branch's Emulator Playback Smoke Test with `verify_signed_upgrade=true`. It checks all three APIs before building an internal signed candidate and testing installation over v0.3.0. It does not publish a release. Missing signing secrets fail this check.
6. Complete the [release audit](docs/ORBIT_RELEASE_AUDIT_2026_10_18.md) and physical-device gates before dispatching [Publish Signed APK](https://github.com/hozefa111/Apk/actions/workflows/release-apk.yml) on approved `main`. That workflow publishes immediately; it is not the internal candidate check.
7. Signed releases with the existing certificate install over signed v0.3.0 without uninstalling. Use **Settings → Check for updates** for a user-initiated check. If Android reports a certificate conflict, investigate the installed/debug identity; do not clear saved data or uninstall a signed installation as an update workaround.

Android never permits an ordinary third-party app to silently install updates without user approval. The Updates screen is explicitly user-initiated and contacts only GitHub releases, not analytics services.

## Build behavior

- `build-apk.yml` produces a debug APK for development and unit tests; that debug build is **not update-signature-stable** between clean GitHub runners.
- `release-apk.yml` produces a release-signed APK **only when both private GitHub Actions secrets exist**. Otherwise, it exits without publishing.
- The release workflow verifies the APK signing, attaches an APK and a SHA256SUMS file, and creates or updates a GitHub Release for the current versionName.
- To publish the next release, increase *both* `versionCode` and `versionName` in `app/build.gradle.kts`. Keep the keystore unchanged.

Never place signing passwords, keystores, base64 secrets, or signing backups in Git commits or public build artifacts.

## Public stable identity

Verified v0.3.0: package `com.orbitplayer.app`, versionCode 13.
Signing-certificate SHA-256:
`27d8f4fd282016b5829348caace9ffb47dbd36f3ea1cfd7819569708274fbda2`.
The development branch uses code 14 / v0.3.1. The internal workflow compares the
candidate certificate before attempting an in-place update. It retains only the
APK and public QA evidence, never the private keystore. Emulator migration uses
real stable playback state and identified legacy-format collection fixtures;
physical-user migration remains a separate acceptance test.
