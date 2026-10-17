# Verification record — 2.2 development

Web reference: `hozefa111/hh` `8b2189d28d7bd9d9c21f509e87bf89717816898f`.
Native starting point: `f9adf83ba74112899b7a1b0b126bc29ceb910b3a` (v2.1).
Date: 2026-10-17 UTC.

## Production safety

HH source checkout is unchanged. The production schema/scoring audit used GET only:
6 players, 324 rounds, 19 sessions. All 324 saved rounds matched HH scoring, with
valid player/partner roles and bids. No account sign-in or production write was
performed by development/tests. No private production snapshot is committed.

Repeat the read-only audit explicitly with:
`python3 scripts/audit_firestore_readonly.py --project patti-pro-a61d8`.

## Automated checks

Latest completed local run: **60 JVM tests passed, 0 failures/errors**; 840 HH
scoring vectors are checked within the parity test. Debug/sandbox app and
instrumentation APK compilation passed. Lint: **0 errors, 20 warnings**.
`verifyReleaseSigning` was also exercised and correctly refused release without
the missing stable signing configuration.

- Original HH function supplies 840 bid/role/result scoring vectors (3–8 players).
- JVM tests cover score validation, gameplay wins and streaks, zero-score session
  hot-streak behavior, head-to-head, month/year boundaries and 23/25-hour DST days,
  retry payload equality, durable JSON draft preservation, archive project/path
  restrictions, duplicate records, large archives, backup version fingerprints,
  and HH legacy JSON conversion.
- Debug and sandbox APKs plus sandbox instrumentation compile locally.
- Android lint runs without error; remaining warnings include existing hardcoded
  English UI text, intentionally pinned dependencies and target SDK 34.
- Ordering regression tests cover active sessions first, ended sessions by end
  time, nanosecond round timestamps and descending Firestore document-ID ties.
- A separate sandbox application compiles only local `demo-three-patti` emulator
  destinations. Instrumentation writes refuse to run in production variants.
- Device tests cover idempotent create/retry, conflicting payload rejection,
  versioned writes/deletes, server timestamps, backup/restore, stale-backup clear
  rejection, full clear/restore with players retained, native navigation,
  full round form, image preview/pagination, search and settings recreation.
  Login restoration during the dialog onShow callback is also exercised to guard
  against dereferencing a dismissed/null dialog. A lifecycle-owned Auth observer
  refreshes SDK session changes and is removed when the activity gateway stops.
  Their result must be recorded below before claiming these paths device-verified.

## Initial CI device run and corrections

The first matrix run on `c202a38` compiled and passed JVM/lint checks. UI search,
settings and navigation passed on API 26 and 35; full form/share preview passed
on API 26 and 35. It exposed a Firestore Emulator DELETE query-precondition
parsing issue (reproduced separately against the local emulator), plus timing
races in direct-gateway tests and an asynchronous preview dialog focus check on
API 30. Delete now uses a version-checked atomic commit, admitted writes refresh
an invalidated snapshot, and tests wait for stable sync and the intended dialog.
Corrected commit `0df3607` passed all four instrumentation tests on each API 26,
30 and 35 (12 device test executions), including the complete round form and
share preview flow. Subsequent ordering/layout fixes and screenshot/gallery
coverage require another run for the final source; the initial failures are not
counted as passes.
A regression check also simulates a second client renaming a player during Search;
results update without rebuilding the query field or losing focus. Gallery checks
on API 29+ require all paginated images to be saved, and CI collects native screen
captures for visual inspection. Gradle must retain test APKs until screenshot
collection finishes; missing image artifacts now fail verification. Captures use
app-private files and `run-as` so Android 11 scoped storage does not block retrieval.
Visual inspection found an unsupported card-brand glyph and ambiguous zero-score
dashes; the brand now uses supported text and score previews/cards show `0`.
Long image previews also use a full-width scrollable viewport rather than shrinking
the whole ranking page into an unreadable strip. Negative best-round and MVP
scores also avoid an incorrect extra plus sign.
The legacy API 29 smoke workflow also enables KVM, matching the matrix jobs,
so input commands are not timed out by software-only emulation.
The local software-only Android emulator could not maintain its package service,
so no successful local device run is claimed.

## Release gates still required

1. Device instrumentation results and visual inspection of the exact final build.
   CI matrix is configured for Android 8/API 26, Android 11/API 30 and Android 15/API 35;
   a configured matrix is not a passed result.
2. A stable private release keystore, signing variables and installed-v2.1 signing
   certificate comparison. User confirmed no stable signing setup is available.
   `verifyReleaseSigning` fails closed until configured.
3. Production Firebase Android registration/configuration and real-device account,
   permission and optional FCM delivery verification. User confirmed Android
   registration/config is unavailable. No production configuration is created.
4. Side-by-side HH/native visual inspection and full long-name/accessibility review.
5. Portable archives are limited to 16 MB. Large mutations use recoverable batches,
   so completed batches persist on interruption; this is not whole-operation atomicity.
   Browser storage requires an explicit JSON export/import; Android cannot read
   a browser's localStorage directly.

No final release APK is published or represented as complete. Debug/sandbox APKs
are internal verification outputs. Native CI uploads reports rather than an
incomplete APK.

## Large-history recovery follow-up

Baseline commit `6f6998d` passed Build Native APK, the API 29 read-only smoke test,
and all six sandbox instrumentation tests on each API 26/30/35 (18 executions).
The 500-document mutation and 10,000-document pagination caps are now removed.
Rename/clear/restore/import use preconditioned batches, a durable per-account journal,
full conflict preflight and local recovery snapshots. Settings can resume or explicitly
stop remaining batches; unrelated writes stay locked while a journal is pending.
Resuming compares live contents/versions, skipping ambiguous commits already applied.
Recovery files use atomic writes and exports are validated against the import size limit.
New unit and 501-record device tests require verification for the new source.
