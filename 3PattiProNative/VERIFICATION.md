# Verification record — 2.2.0

Date: 2026-10-17 UTC. Web reference: `hozefa111/hh`
`8b2189d28d7bd9d9c21f509e87bf89717816898f`. Native v2.1 baseline:
`f9adf83ba74112899b7a1b0b126bc29ceb910b3a`.

## Completed evidence

- 68 JVM tests pass for debug and release, including 840 vectors from actual HH
  scoring and 64 fixtures from actual HH endSession. All 324 production rounds in
  the read-only audit matched HH scoring (6 players, 19 sessions).
- Application source through `fa87081` passed all nine sandbox instrumentation
  tests on API 26, 30 and 35: 27 executions, zero failures/errors. Latest complete
  verification run: 39028539761, source head `e40de88` (later changes were test/docs).
  Build run 39028539757 and API 29 read-only smoke 39028539815 also passed.
- Tests cover version preconditions, server timestamps, idempotent retries,
  conflicting payloads, stale backup rejection, clear/restore retaining players,
  501-record operations and restart recovery, search refresh, account dialog
  lifecycle, settings recreation, full round form, share pagination and 2x fonts.
- Actual API 26 DocumentsProvider folder save was verified by decoding the saved
  PNGs. API 30/35 gallery saves also passed. Single/multiple share intents were
  opened by a separate test APK UID, verifying grants, ClipData, MIME and PNG decode.
- Fourteen screenshots per platform were collected; navigation, large fonts and
  long-name share layouts were reviewed. HH browser references used synthetic data.
- Lint has zero errors; warnings include hardcoded English labels, pinned
  dependencies, target SDK 34 and intentional synchronous private recovery commits.
- Local release build uses the owner-approved stable RSA-3072 identity. The signing
  guard refuses absent configuration. APK Signature Scheme v2 is checked locally.
- Release version 2.2.0 (code 11) changes only version metadata from the above app
  implementation. The API 29 smoke workflow now builds/tests/launches the release
  variant with a disposable CI certificate; the distributed APK uses the private
  stable identity. Its completed result is included in the delivery report.

Earlier failed device runs exposed timing races, export persistence, delete
preconditions and large-font wrapping. They were corrected and rerun; failures
are not counted as passes. Local software emulation was unstable, so device
results above are from GitHub Actions Android emulators, not physical phones.

## Production safety and account limits

Original HH checkout is unchanged. The production audit used GET only. No test
signed into a production account or wrote production game data, Firebase rules,
project settings or Android registrations. Test writes use the separate compiled-in
`demo-three-patti` sandbox destination and synthetic fixtures only.

Firebase Auth/Firestore use explicit FirebaseOptions and do not require a
`google-services.json` file for the current email/password and database paths.
The missing Android registration does not by itself block these APIs. Actual
owner-account sign-in and cross-device production writes were not exercised;
they remain subject to the existing project API restrictions and security rules.
No credentials were borrowed from HH's source.

HH's notifications.js helper is not referenced by index.html or app.js, and its
messaging SDK is not loaded by the active web entry point. FCM is not implemented
or claimed as active web parity. Adding push requires Android client registration,
backend delivery setup and a separate end-to-end device check.

## Signing and installation

The user approved a fresh install and new stable identity. This APK cannot update
an old v2.1 installation signed with another key. Export any app-private recovery
backup before uninstalling. Future updates must reuse this new key.

Certificate SHA-256:
`DC:06:97:A0:35:26:61:2B:C5:4C:3D:9A:D0:B4:A1:0E:9E:D9:B3:B6:AD:22:E4:E5:32:69:F9:04:A7:2B:A9:B4`.
SHA-1: `E3:71:C5:BA:75:06:0A:3E:23:2E:81:76:80:78:1E:CA:1D:BF:49:0B`.
Private keystore and password variables remain outside GitHub and APK artifacts.

## Operating limits

Portable archives are limited to 16 MB and validated before import/export. Firestore
record/type/depth/size validation prevents partially starting a malformed import.
Large rename/clear/restore operations use version-checked batches of at most 400
records/6 MB with a durable per-account journal and recovery snapshot. Completed
batches persist on interruption; this is not whole-operation atomicity. Settings
can retry or explicitly stop; unrelated writes stay locked while recovery is pending.
No offline mutation queue exists. Browser localStorage needs explicit JSON export;
Android cannot silently read it. Recovery snapshots are app-private and disappear
on uninstall unless exported through Settings.
