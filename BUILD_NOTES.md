# Build notes and engineering decisions

The project remains Kotlin + native Views/XML + Media3, minSdk 26, compileSdk 36,
targetSdk 35, Java 17 and Gradle 8.13. App ID and production signing configuration
are unchanged. This branch remains development version 0.3.1 / versionCode 14.

See [the current audit and evidence](docs/ORBIT_RELEASE_AUDIT_2026_10_18.md).
The unchanged takeover baseline compiles locally in debug and optimized release,
and its unit tests pass. Current-source checks must be distinguished from that
baseline; a compiled development APK is not a verified production release.

## Architecture

- MainActivity snapshots view/preferences state; LibraryEngine filters, sorts and
  groups immutable metadata on a separate worker. A generation token rejects
  stale results. RecyclerView's existing asynchronous diffs remain intact.
- ThumbnailLoader shares one 12 MiB cache between folders, videos and Continue
  Watching. Two workers decode; the pending queue is bounded. Recycled targets
  cancel outstanding work. URI, modified time and size form the cache key.
- Primary-storage folder keys and existing content URIs stay compatible with
  previous preferences. Removable-storage folders additionally include the
  MediaStore volume so equally named paths on different volumes cannot merge.
- Collections retain their existing local preferences and ordered JSON arrays.
  Rename is an atomic preference edit and refuses an existing destination name.
  No organization action moves or deletes original videos.
- Queue serialization runs on a single worker; an explicit restored flag prevents
  an intentionally empty external-file queue from restoring stale library items.
- ExoPlayer owns audio focus and noisy-headset handling. The platform MediaSession
  routes active-player controls. There is no background MediaSessionService or
  dedicated playback notification in this branch.
- Subtitle URI, MIME, track parameters, pause intent, touch lock and sleep deadline
  survive activity recreation. A stopped player retains the active session's
  exact position separately from the near-end persistence/completion policy.
- Network access remains user initiated through the update checker. It selects
  only the expected Orbit APK asset name in this shared repository. It never
  silently installs an update. Playback accepts local content/file URIs.

## Validation

```bash
python3 scripts/check_layouts.py
python3 scripts/check_library_ui.py
gradle --no-daemon :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease \
  :app:assembleDebugAndroidTest
```

With a running emulator/device and generated H.264/AAC `smoke.mp4`:

```bash
bash scripts/run_emulator_smoke.sh
gradle --no-daemon :app:connectedDebugAndroidTest
python3 scripts/create_media_fixtures.py qa/media
python3 scripts/run_media_matrix.py qa/media
```

Instrumentation expects `orbit_smoke.mp4` to have been indexed by the smoke script.
Its collection tests use separate QA preference files. Subtitle assets are packaged
only in the test APK. Synthetic fixtures prove repeatable cases; physical-device
codec coverage, audible output, SD-card behavior, long-session battery/performance
and a signed in-place upgrade still require separate evidence.

Release signing uses the existing private PKCS12 key through GitHub Actions secrets.
No production signing key is available in this workspace; none is generated or
substituted. Do not distribute debug APKs as updates to production installs.
