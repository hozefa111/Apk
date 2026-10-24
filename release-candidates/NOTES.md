3 Patti PRO v2.7.13

- Recover uncertain moves using the original command ID and server receipt; refresh expired authentication once and avoid redundant presence repairs.
- Sequential public-card playback: correct seat, 1.5-second visibility, complete-trick hold, winner and captured points before collection.
- Single-tap selection and double-tap play, retaining Confirm and duplicate-command protection.

Same Android package, stable signing certificate, existing accounts, rules and score data. No production database migration or security-rule change.

Verification report will be attached after release checks complete. No physical-phone verification claimed.

Verification details

- Web/game source: hozefa111/hh commit cb76096 (client and focused browser tests).
- Android gameplay source: hozefa111/Apk commit 05c2565; version 2.7.13 / code 34.
- Root causes: pending commands previously required manual intervention without receipt reconciliation; reconnect did not refresh credentials; healthy presence registrations could be unnecessarily repaired on resume; snapshot-only animations selected the latest card and missed intermediate moves.
- Changed: online/client.js, online/game.css, generated runtime/client.js, focused recovery/playback/double-tap browser tests, package test scripts; matching Android assets/checksum manifest and app version.
- Passed locally: 91 engine/service tests, 193 release JVM tests, release lint, 7 Firebase Emulator privacy/security tests, focused lost-response/token-refresh/Ready recovery tests, 4/6-player playback and double-tap tests, lobby finger-scroll regressions, compact layouts and persistent-control regressions.
- APK package and certificate match the prior release. Bundled source/runtime/CSS match the tested source. Certificate SHA-256: dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4.
- Publication is gated by Android 11/15 lobby touch verification (run 40065601803) and installation of this exact signed APK over v2.7.12 with a preserved app-data marker and startup check (this workflow).
- Android touch tests use the demo Firebase environment. Lost-response and token-refresh browser tests use controlled responses. No physical-phone or production-outage test claimed.
- Reconnection still requires internet and valid account credentials. Automatic uncertain-command retries are bounded; unresolved failures retain the original command for manual retry. Pending command storage is tab-scoped; force-stop/process-death restoration is not certified.
- No game-rule, bot-policy, Firebase security-rule, account, production-data or scoring migration changes. Only committed public cards enter the visual queue; private opponent hands remain hidden.
- The local Database Emulator initially failed under proxy configuration; the isolated retry without proxy environment passed all seven tests. The local Android emulator could not start without KVM; Android verification runs on hosted accelerated emulators.

Android 11 first-pass verification failed at create/stop gesture delivery (run 40064823099); no API call followed those failed taps. The retry instruments click delivery and requires stable unobstructed touch targets before injecting gestures. Runtime APK bytes remain unchanged. The release is blocked until this stricter retry passes on both Android versions.
