# Library responsiveness and recovery QA — 19 October 2026

Baseline `e55a45d`: [build/unit/lint](https://github.com/hozefa111/Apk/actions/runs/39147859303)
and [API 26/33/35 suites](https://github.com/hozefa111/Apk/actions/runs/39147859284)
passed. PR #10 remains open on `orbit-pro-qa-hardening-oct17`.

## Changes under verification

- Filtering captures UI choices on main, then reads preference snapshots and
  playlist JSON on the existing worker. Only Continue Watching queries construct
  the complete resume-position map; ordinary search does not copy it.
- Continue-card selection runs on the existing library worker with immutable
  inputs and generation checks. A single pass prefers the last played accessible
  unfinished video, otherwise the most recently modified unfinished video. Existing
  resume thresholds and preference keys remain unchanged.
- The previous continue card is made unavailable immediately while refreshing;
  hidden-folder actions and scan failures refresh its selection as well. Generation
  checks reject older callbacks and activity destruction cancels pending work.
- Startup errors use real paragraph breaks and actionable file-access guidance
  instead of referring to an error report that may not exist.
- No new dependency, executor pool, permission, application ID or version change.
  Existing asynchronous list diffs and bounded thumbnail decoding are retained.

## Added regression coverage

- Continue selection: preferred item, fallback, hidden folders, missing/deleted
  media, unknown duration, unstarted/completed media and no eligible items.
- Actual Activity/filter/diff/RecyclerView flow with 0, 1, 10, 100 and 1,000
  synthetic metadata entries. Verify counts, end scrolling, search and replacement
  of an older query. Entries share an indexed smoke video with distinct URI query
  keys: this is not a 1,000-file disk/MediaStore scan or a phone speed benchmark.
  The same Activity test checks asynchronous continue-card loading and clearing
  without an older selection becoming clickable again.
- Natural Play All progression past missing and corrupt middle entries; informative
  error at the final corrupt entry; replace that test file with playable bytes and
  activate the real Retry button. Queue entries and files must remain intact.
- Existing generated metadata unit fixtures still cover up to 10,000 entries;
  their elapsed times measure computation in the test runtime, not phone UI speed.

Verification results and initial failures are recorded below. A passing build or selected audio
track does not establish audible codecs, hardware performance or release readiness.
Physical-device acceptance, long sessions, removable storage and signed upgrade
verification for a new final candidate remain separate gates. Original web source
is still unknown and exact parity remains unverified. No new APK delivery or public
release is part of this QA batch.

## Local environment finding

The local software API 26 emulator reported `ANR in system` at 00:55:31 UTC and
`ANR in com.android.systemui` at 00:57:03, before Orbit's first process started
at 01:00:08. A window dump during the new Retry test showed focus held by the
System UI ANR dialog, with a second system ANR dialog underneath. The first local
run's Retry assertion failed while those dialogs blocked accessibility input;
it is retained as a failed run, not changed into a pass. Diagnostics were captured
before interacting with the dialogs. A clean focused run is required to establish
actual recovery; its result is recorded below. No app ANR or physical-phone performance conclusion follows from
these OS boot dialogs.

## First CI result and test portability correction

At `9c6eb35`, [build, both unit variants and lint](https://github.com/hozefa111/Apk/actions/runs/39183255502)
passed (25 unit tests per variant). The [expanded emulator run](https://github.com/hozefa111/Apk/actions/runs/39183255496)
failed the new Retry-button input assertion on all three APIs; all other 13
instrumentation tests per device passed, as did native navigation, permissions
and the generated media matrix. These failures are not presented as green.

The button matcher now accepts Android's all-caps label transformation and
records focused package/matching labels in failure diagnostics. It still must
activate the actual Retry button and verify the same repaired URI reaches READY,
has no player error and is set to play. After dismissing both local OS ANR dialogs,
the corrected focused recovery test passed on API 26, including missing/corrupt
middle-entry skipping and final-file recovery. The fresh full CI result follows.

## Corrected source verification

Source: `a17c85b15d63d28e41a66cb0d9e71b0cbfd00d66` (application code matches
`9c6eb35`; only the Retry test and documentation changed).
[Build/unit/lint 39183899969](https://github.com/hozefa111/Apk/actions/runs/39183899969)
passed: debug, optimized release and instrumentation APKs compile, both lint
variants complete, and all 25 unit tests pass in each variant.

The [corrected full suite](https://github.com/hozefa111/Apk/actions/runs/39183899936)
and [internal signed-upgrade run](https://github.com/hozefa111/Apk/actions/runs/39183907391)
completed for this exact source; results are recorded below. Signed validation
uses the existing production certificate in Actions
and a disposable emulator; it does not publish a release or establish phone QA.

The corrected full suite **passed on API 26, 33 and 35**: all 14 instrumentation
tests per device, native navigation/player UI, permission recovery and the
generated 12-case media startup/error matrix. This includes the actual Retry
button input and successful playback of the repaired final file. The matching
three smoke jobs in the signed workflow also passed before its upgrade job began.
Generated codec results do not establish audible output or universal codec support.

## Verified internal signed candidate

[Run 39183907391](https://github.com/hozefa111/Apk/actions/runs/39183907391)
passed all three native suites and the signed v0.3.0 upgrade job. Android accepted
`adb install -r` with the existing production certificate, without uninstalling
stable or clearing its data. Legacy-format favorite/ordered-playlist fixtures and
real stable playback's resume/history were preserved. The optimized candidate
then resumed and advanced beyond the position saved by stable playback.

The downloaded artifact was independently verified with `apksigner` and `aapt`:
`com.orbitplayer.app`, 0.3.1 / code 14, minSdk 26, targetSdk 35. Certificate SHA-256
is `27d8f4fd282016b5829348caace9ffb47dbd36f3ea1cfd7819569708274fbda2`.
APK SHA-256 is `19ba69dbff2659afd5c5204f9ae119420390cdbc816449b19724f508c0ed3d93`.
See [the signed evidence record](qa/signed-upgrade-2026-10-19-result.json).

This is an internal QA candidate for source `a17c85b`, not a published release.
Later documentation-only commits do not change its tested application/test source.
Physical-phone speed, audible codecs/Bluetooth/interruption behavior, SD-card
handling, long sessions and broader settings/folder migration acceptance remain
open. The original web reference remains unknown. No merge, tag or public release
was performed, and the previously delivered APK was not overwritten.
