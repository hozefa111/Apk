# Verification record — 2.2 development

Web reference: `hozefa111/hh` `8b2189d28d7bd9d9c21f509e87bf89717816898f`.
Native starting point: `f9adf83ba74112899b7a1b0b126bc29ceb910b3a` (v2.1).
Date: 2026-10-17 UTC.

## Production safety

HH source checkout is unchanged. Production Firestore was accessed with GET only:
6 players, 324 rounds, 19 sessions. All 324 saved rounds matched HH scoring, with
valid player/partner roles and bids. No account sign-in or production write was
performed by development/tests. No private production snapshot is committed.

Repeat the read-only audit explicitly with:
`python3 scripts/audit_firestore_readonly.py --project patti-pro-a61d8`.

## Automated checks

Latest completed local run: **46 JVM tests passed, 0 failures/errors**; 840 HH
scoring vectors are checked within the parity test. Debug/sandbox app and
instrumentation APK compilation passed. Lint: **0 errors, 20 warnings**.
`verifyReleaseSigning` was also exercised and correctly refused release without
the missing stable signing configuration.

- Original HH function supplies 840 bid/role/result scoring vectors (3–8 players).
- JVM tests cover score validation, gameplay wins and streaks, zero-score session
  hot-streak behavior, head-to-head, month/year boundaries and 23/25-hour DST days,
  retry payload equality, durable JSON draft preservation, archive project/path
  restrictions, duplicate records, atomic size limits, backup version fingerprints,
  and HH legacy JSON conversion.
- Debug and sandbox APKs plus sandbox instrumentation compile locally.
- Android lint runs without error; remaining warnings include existing hardcoded
  English UI text, intentionally pinned dependencies and target SDK 34.
- A separate sandbox application compiles only local `demo-three-patti` emulator
  destinations. Instrumentation writes refuse to run in production variants.
- Device tests cover idempotent create/retry, conflicting payload rejection,
  versioned writes/deletes, server timestamps, backup/restore, native navigation,
  full round form, image preview/pagination, search and settings recreation.
  Their result must be recorded below before claiming these paths device-verified.

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
4. Side-by-side HH/native visual sign-off and full long-name/accessibility review.
5. Operations exceeding 500 affected documents still fail safely before mutation;
   full unbounded web parity is not claimed. Browser storage requires an explicit
   JSON export/import; Android cannot read a browser's localStorage directly.

No final release APK is published or represented as complete. Debug/sandbox APKs
are internal verification outputs. Native CI uploads reports rather than an
incomplete APK.
