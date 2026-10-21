# 3 Patti PRO v2.7.7 — verification report

## Delivered behaviour

- Existing-name joining needs no admin approval. Players choose **Take seat N**.
- Full tables and mid-hand arrivals wait. Active-hand seats remain fixed.
- Atomic server validation prevents duplicate seats, foreign-player claims and invalid seats.
- Reconnecting restores the same seat/cards. Legacy pending invitations can self-confirm.
- Seating changes reset readiness. Admin remains the final Ready participant.
- **Manage table → Remove**, swaps, bot replacement, Pause/Resume and Stop Session remain admin-only. Finish or void an active hand before removal/replacement. Earlier points/history remain saved.
- Same Android application, stable signing, manual/voice entry, scoring and historical records retained.

## Actual tests

- 72 rules/service tests passed, zero failures/skips.
- 7 Firebase security tests passed.
- 54 browser checks passed: actual self-seat UI in both modes, full multiplayer hands, scoring, direct 250, reconnect, privacy and manual-tracker regressions.
- 16 free-backend tests passed including actual Worker runtime integration, zero failures/skips in emulator-backed run.
- 192 native Release JVM tests passed.
- Android 11 / API 30: 48 instrumentation tests passed, zero failures/errors/skips.
- Android 15 / API 35: 48 instrumentation tests passed, zero failures/errors/skips.
- Signed Release build succeeded. Signature and all 15 shared-asset hashes verified against tested source.
- Hosted web client matches tested source. Existing free Worker deployment and API browser preflight verified. No Firebase rules deployment or destructive data migration.

Web CI: https://github.com/hozefa111/hh/actions/runs/39599597018

Android CI: https://github.com/hozefa111/Apk/actions/runs/39599613109

Source: https://github.com/hozefa111/hh/pull/10 and https://github.com/hozefa111/Apk/pull/18

Worker version: `b4ff4fb2-e2f3-4077-be1c-c48c83a29f63`.

## APK

Package: `com.hozefa.threepattipro.nativeapp`; version **2.7.7 (28)**.

SHA-256: `7a462a570db378eb98cb08960d87e79de331d78b0bd9498c01f5de9cf345d152`

Signer SHA-256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`.

Install over the existing stable-signed app; no uninstall required.

Physical-handset checks and a live production multiplayer hand were not performed. Gameplay/security tests used demo emulators and actual browser/Worker runtimes; Android instrumentation used emulators. Physical-device performance is not certified.
