# 3 Patti PRO v2.7.8

Same Android application, package `com.hozefa.threepattipro.nativeapp`, version code 29.

## Changes

- Keep the table, hand, bid selector, focus and scroll position in place during real-time updates; update only changed content. Preserve history selections and animations.
- Reduce mobile rendering work: remove expensive blur/card filters and use transform/opacity animation. Avoid redundant timer writes.
- Replace browser-origin prompts with accessible in-app Stop Session choices: Finish hand & stop, Void hand & stop, Keep playing. Android Back closes the dialog first. Add positioning fallbacks for older WebViews.
- Protect bid submissions against stale touch gestures and promptly release controls after accepted/rejected requests. Show a delayed-server notice rather than inviting duplicate submissions.
- Batch authoritative backend reads, avoid unnecessary global-role reads on normal moves, and respect persisted retry backoff after transient backend failures. Identity, ownership, legality, deadlines and scoring remain validated.
- Let a shared roster name be selected on another device without an obsolete global profile lock. Active seats remain bound to their participant identity: another identity cannot take over a current seat or read its private cards. Joining and self-service empty seats require no admin approval.
- Keep the existing English game, manual/voice entry, unified human lifetime scoring, session scores and historical records. No destructive production tests or data migration. Free backend retained.

## Verification

The signed Release build and lint completed successfully. Release JVM tests: **192 passed**, zero failures/errors/skips. Lint: zero errors, 38 existing warnings. All 15 shared game source assets match the APK bundle; the generated asset manifest is verified separately.

Package: `com.hozefa.threepattipro.nativeapp` · version **2.7.8** · code **29**.

APK SHA-256: `3f4f31cf44148e2d16b4a94ca43418b8174300087b61b04f6de4f1807b6a702c`.

Stable signing certificate SHA-256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`.

Final web CI passed on commit `cd80d316acd425247c54eb84f769e69b8ecbfb0b`: [run 39624743341](https://github.com/hozefa111/hh/actions/runs/39624743341).

- Rules/service: **72 passed**, zero failures/skips.
- Firebase emulator security: **7 passed**, zero failures/skips.
- Browser journey, multiplayer, layouts, dialogs, reconnect, deletion, bidding and retained-render checks: **85 passed** across 10 reports.
- Free backend adapter plus actual Miniflare/Firestore emulator integration: **18 passed**, zero failures/skips. The preliminary adapter-only step skips its 2 emulator-dependent scenarios; the final integration step runs and passes all 18.

Android 11 / API 30 instrumentation: **50 passed**, zero failures/errors/skips, on final verification source `061b2f6fb3f6c1ee89c31c74a53ee5273e1e11e0`. This includes the new retained controls and Stop dialog/system-Back regressions, plus existing manual/voice/history/session tests. Android screen tests use the sandbox variant and emulator backend; the production-configured signed Release APK is built separately from the same source.

Android 15 / API 35 instrumentation: **50 passed**, zero failures/errors/skips, on the same final verification source.

[Android Actions run 39627351435](https://github.com/hozefa111/Apk/actions/runs/39627351435).

Initial verification exposed an older-WebView CSS positioning problem and a new test fixture missing its connected state. Both were corrected before the final run. Two initial Android 15 runs lost their emulator before completing; neither partial run is counted as a pass. The final verification bounds the test JVM memory, disables emulator Vulkan, and captures host diagnostics. Both complete 50-test suites passed with that configuration. These were CI-only changes; the signed app content did not change.

## Performance limits

Controlled browser tests verify that 80 unchanged live updates preserve DOM nodes, focus and bid selection with zero structural mutations, including under 4x CPU throttling. This is a synthetic rendering check, not a physical-phone FPS measurement or a guarantee of network latency.

No physical Android phone was available for this release verification. Android emulator results are reported separately. Intermittent internet/service delays can still occur; this release prevents excessive retry loops and unnecessary redraws and checks recovery from an injected transient backend failure.

## Delivery and deployment

- Web implementation: [PR 11](https://github.com/hozefa111/hh/pull/11), [PR 12](https://github.com/hozefa111/hh/pull/12), [PR 13](https://github.com/hozefa111/hh/pull/13). Final implementation merge: `8967ce8b45d514915445626bc459b5206e078801`; subsequent web commit `461b597d6` adds documentation only. Hosted runtime JavaScript and CSS hashes match the tested source.
- Android implementation: [PR 19](https://github.com/hozefa111/Apk/pull/19), merged as `22fe2e290b29c9969b1f389c982e2a12435581f4` into `three-patti-pro-native`.
- The last Android commit changes CI resources/diagnostics only. App source and bundled game assets match the signed Release build verified before that change.
- Existing free Worker deployment: version `43605f86-c7df-453f-a00e-815af5110725`. Existing Firebase Spark project retained, no paid backend introduced.
- No outstanding automated release-gate failures. Physical-device responsiveness remains unverified. Free-service/network latency is not guaranteed.

Install this APK as an update to the existing stable-signed 3 Patti PRO app. Completed sessions, human score history and existing player records are preserved.
