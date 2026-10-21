# 3 Patti PRO v2.7.6 — remaining-card shape fix

Same application `com.hozefa.threepattipro.nativeapp`, version 2.7.6, code 27, with the existing stable signer. All game text remains English.

## Reported issue and correction

The user's final card filled almost the entire hand-panel width while its height remained fixed. The grid used available panel width regardless of how many cards remained. Earlier tests verified viewport fit and overlap but did not constrain aspect ratio across late-hand counts.

The hand grid now derives its width from column count and a bounded card height, targets a width/height ratio of 0.70 and stays centered within its hand panel. Available width caps larger hands. One to four cards retain a compact single row; larger hands retain the full two-row view. Portrait heights are 70–92 px; short-landscape heights are 60–76 px. Fixed-height fallbacks cover WebViews without newer sizing functions. Smaller landscape corner/suit typography remains within the face.

No game rule, scoring formula, turn/timer, bot policy, admission, replacement or database interface changed. No backend deployment, security-rule change or data migration is needed. Existing manual/voice entry, sound, animations and the v2.7.5 pause/reconnect/void/replacement flow are retained.

## Verification completed locally

- Ten grouped compact browser checks passed with no page errors. The new regression uses actual legal engine play to capture every remaining-card count: 10→1 in four-seat mode, 7→1 in six-seat mode, across six viewports (102 late-hand configurations), in addition to the existing phase/layout checks.
- Each card is taller than wide, bounded to normal dimensions, and the hand stays centered. Tests assert no local-card overlap, hand swipe, page overflow or hand overlap with play controls. Existing bid controls, stale-draft handling, maximum state, visible deal animation, sound and drawer interactions also passed.
- Viewports: 320×568, 360×640, 390×844, 412×915, 740×360 and 1280×800.
- 192 native Release JVM tests passed. Signed Release APK and sandbox instrumentation target built successfully. All 15 shared asset hashes match the web source; package/version/stable certificate verified.
- Android instrumentation was extended to check 3, 2 and 1 remaining cards for both modes inside the actual bundled WebView.

## Final release gate

All required release gates passed for the tested environments:

- [Web CI](https://github.com/hozefa111/hh/actions/runs/39585190303): success; 67 rules/service tests, seven security tests, 52 browser checks and all 14 backend integration tests passed. Runtime backend checks had no skips. This includes full multiplayer, bidding, privacy, sound, animation, recovery and replacement regressions alongside every remaining-card count.
- [Android CI](https://github.com/hozefa111/Apk/actions/runs/39585195039): success; API 30 and API 35 each passed all 47 instrumentation tests without failures/errors/skips. The bundled-WebView test now also verifies card proportions and hand centering for 3, 2 and 1 remaining cards in both modes.
- Web source `8f5c0e5c69967be0dc69d9aca87b4113209f909e`, merged through [PR 9](https://github.com/hozefa111/hh/pull/9) as `b49c4a2527f03b31bbaade00fa5a8e2ebbbbd085`.
- Native source `cb654830c2969633ccaef975c6d09d38463f3ae3`, merged through [PR 17](https://github.com/hozefa111/Apk/pull/17) as `27f3033babda3992020efb5a115911a439191423`.
- Published web CSS fetched successfully and matches the APK's bundled source byte for byte. The existing free backend was retained without deployment or production test writes.
- Signed APK identity, version, certificate and all 15 shared-file hashes verified. Ready to install over the existing stable-signed 3 Patti PRO app. No known failing release check remains.

## Limits and artifacts

Browser previews are local engine fixtures. Actual Android checks use emulators; a physical-handset playtest was not performed. The attached screenshot previews show the fixed final-card proportions. No claim is made that every handset/vendor/font combination was tested.

APK SHA-256: `1ca4d1be16f8bdf5ee3126b30e29add84d41c956bdb8c56c02261290a6549649`.
Certificate SHA-256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`.
