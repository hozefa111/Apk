# 3 Patti PRO v2.7.12 — Android lobby scroll fix

Verified signed release, same existing app and signing identity. [Web source PR16](https://github.com/hozefa111/hh/pull/16) and [Android PR23](https://github.com/hozefa111/Apk/pull/23) are merged.

## Confirmed root cause

New public room documents omit `hand`. The shared engine intentionally returns a falsy `undefined` for `active(room)` in that case. The UI used `const live = !!room && active(room)`, then `classList.toggle('live-game', live)` and a similar bidding toggle. `toggle(token, undefined)` means toggle, rather than force removal. Repeated callbacks therefore alternated an actual lobby between normal document layout and the active card table's fixed-height, overflow-hidden layout. The additional scroll-position read/restore could repeatedly return the collapsed document to the top.

This was reproduced with unchanged v2.7.11 UI bytes on Android 11 and 15 using actual injected finger gestures after real emulator-backed room creation. Both baseline tests failed. Logs showed the **lobby seat DOM still present**, body `reduced live-game bidding`, scrollY0 and document height equal to viewport (616px/API30,592px/API35). The baseline made6/13 imperative scroll calls during that test. This is a confirmed layout/state bug, not evidence of a Firebase server freeze.

A separate browser before/after reproduction with the real public-state shape showed the old layout alternate between640px and1764px on successive renders. The corrected layout remained unlocked with a stable1668px document over all six renders. Earlier browser fixtures with canonical `hand:null` did not reproduce the missing-field bug; the new regression deliberately uses `publicState()`.

## Targeted changes

- `online/client.js`: force `live` to a boolean; never apply live-table/bidding locks to a lobby. Remove per-snapshot `window.scrollY` read/restore. Scroll to top only on actual home/loading/room/table view transitions.
- Keep seat and dock nodes stable across snapshots with logical seat keys. Preserve the existing animation/render batching and subscription lifecycle.
- Move the existing single Ready/Start action into a compact persistent dock. Readiness, admin-last-start, connection and mutation permissions are unchanged. No duplicate Ready control.
- `online/game.css`: preserve dark/green/gold styling; reserve the dock's measured height in the scrollable document. Include safe-area insets, small-screen grids and overflow-safe seat text. Keep notices/pause UI above the dock.
- One `ResizeObserver` measures the dock; unobserve it on exit. Its shared inset also reaches the sibling notice layer. No scrolling touch-event interception or new dependency.
- `online/test/lobby-scroll.browser.cjs`: real public-state missing-hand regression, repeated updates, stable nodes, finger touch streams, visible/disabled Ready, small portrait/landscape and horizontal-overflow checks.
- Android `LobbyScrollTest.java`: demo-only native touch integration for four/six seats, repeated swipes, close/reopen, computer addition, visible Ready and game navigation.
- Existing build/sync scripts generate the matching Android client/runtime/CSS/manifest. Android version2.7.12/code33, existing application/signing identity.

The inspected HTML already has viewport-fit=cover. The existing Android Activity already allows normal WebView scrolling, uses the secure APK asset loader and LOAD_NO_CACHE, and has no custom touch interceptor. No native WebView redesign or blanket overflow hack was needed.

## Verification and packaging

| Check | Actual result |
|---|---|
| Focused Android lobby flow | Both four/six-player tests passed on Android11/API30 and Android15/API35 in [CI39951871418](https://github.com/hozefa111/Apk/actions/runs/39951871418) |
| Tested actions | Real Create tap, repeated native touchscreen swipes during updates, last-seat controls, single unobstructed Ready, Activity/WebView close and relaunch, resume via existing room card, add3/5 computers, Ready, own10/7 cards, table entry, stop/return |
| Android11 scroll measurements | Four seats675px before/after reopen; six seats916/923px; zero scroll-reset calls; seat DOM retained |
| Android15 scroll measurements | Four seats720px before/after reopen; six seats877/881px; zero scroll-reset calls; seat DOM retained |
| Broader Android15 suite | All59 unique checks passed in20+39 shards in [CI39953385788](https://github.com/hozefa111/Apk/actions/runs/39953385788) |
| Broader Android11 suite | 58/59 passed in the full run. The unchanged one failed share-preview test passed alone on a fresh emulator in [CI39955837643](https://github.com/hozefa111/Apk/actions/runs/39955837643). All59 unique check names have passing results using identical app/test source; this was **not** a single green full run. |
| Web/security/Worker integration | [CI39950328721](https://github.com/hozefa111/hh/actions/runs/39950328721) passed:91 engine/service tests,7 security tests, actual multiplayer/browser suites and24 emulator-backed Worker tests with zero final-integration skips. Pre-emulator preflight correctly skipped3 environment-dependent cases. |
| Focused browser UI | Five programs passed57 reported checks, including12 new lobby checks;320x568,390x844 and844x390, both modes, touch input, disabled states, no overflow, existing controls/drawers/trick collection/dialogs |
| Release JVM/lint/build |193 unit tests passed,0 failures/errors/skips; lint0 errors/38 existing warnings; signed release build passed |
| Bundled assets | All16 entries (15 listed assets plus manifest) byte/hash matched; source and compiled runtime contain the fix and `scroll-dock-v1` revision. Actual WebView checked that revision and absence of a service-worker controller. |

Intermediate blocked/failed attempts were not counted as passes. One Android15 attempt had a proven Pixel Launcher ANR that took input focus; a fresh emulator passed. The broad Android11 share-preview attempt timed out; its log also contained a renderer termination, but the cause of that timeout was not established. Share preview uses native Canvas, and no unrelated share implementation was changed. Its original test passed in isolation, including single/multiple PNG delivery to another app UID with read permission. The raw coverage summary explicitly distinguishes the full run and retry.

The sandbox APK was installed by Gradle on actual Android emulators against demo Firebase. Native finger events, real UI taps and actual server commands were used. Fixtures filled the Create fields programmatically; keyboard typing was not certified. The release APK's packaged UI is byte-identical to the tested synchronized assets, and the native Activity/loader code is unchanged.

Release identity:

- Version2.7.12, code33; package `com.hozefa.threepattipro.nativeapp`.
- APK SHA256: `f43587acfff454abc0a8418a61f7d3d846d39f018267ea162d254cb935bb020d`.
- Stable certificate SHA256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`.
- Tested application/test head `df18cb249e137912158537a1ef82ce6d5511cb07`; Android merge `dd8939e400b3c8b80a8302bddad71ae3775d6586` has the identical app tree.
- Web tested head `0d259711b969bb56768e5551419dff3807392151`; merge `8ea0c6595427f85c0adb9d45b6efb68225aad67d`.
- Hosted origin client/runtime/CSS were fetched and match source hashes. No backend, Firebase rules/index or data deployment was needed. Previous v2.7.11 release is retained.


## Preservation and limits

Bot AI, card engine, bidding/scoring rules, service/backend, Firebase rules/schema and tracker code are unchanged. No production QA users/rooms/rounds, migrations or data deletion. Existing player data, histories, sessions and stable application identity remain intact. Emulator writes use demo-three-patti only.

The emulator workflow installs the sandbox variant to safely exercise the actual bundled UI against demo Firebase; release signing and packaged-byte checks are separate. Do not interpret this as a production multiplayer or physical-phone test. Physical-device, vendor-specific WebView and real keyboard behaviour are not certified by these checks.
