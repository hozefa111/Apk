# 3 Patti PRO v2.7.14 — Firebase usage and recovery

Signed release, version code 35. Same package com.hozefa.threepattipro.nativeapp and existing stable signing certificate. Updates v2.7.13 without uninstalling.

Includes reconnect and original-command-ID pending recovery; quota-aware errors/backoff; sequential public-card playback with seat/winner/points visibility; single-tap selection, double-tap legal play and Confirm; lazy hand-history subscriptions, active-only membership queries and fewer redundant membership writes. No game-rule, bot-strategy, account, room, score, database or billing migration.

IMPORTANT LIVE LIMITATION: At 2026-10-24 06:46 UTC, live Firestore still returned 429 "Quota exceeded". Create Room and Stop Session cannot operate while Firebase refuses database operations. Their live success has not been certified in this quota window. This APK does not increase free quota, and quota exhaustion is not permanently solved. Existing rooms/scores are preserved. Only committed public cards are animated; private opponent hands stay private.

Previously completed checks were reused: 193 release JVM tests, 7 privacy/security tests, recovery/receipt/token/Ready tests, 4/6-player playback and double-tap tests, Android 11/15 lobby touch tests. Accepted optimization tests: 2 emulator integration tests, 24 backend unit tests, focused browser subscription/backoff tests. Demo measurements: closed history 78→0 reads; active memberships 102→2 reads; 4-human hand writes 217→214; 6-human hand writes 251→246. These are scenario-specific, not production billing savings. Backend point-read counts per hand were unchanged.

Essential final release checks: release build/lint, stable certificate, bundled-asset checksums, published-web runtime parity; publication is gated on installing this exact APK over v2.7.13, retaining an app-data marker, and successful startup/version check. No physical-phone test claimed; force-stop pending-command restoration is not certified.

Sources: web main d3e43f34a0e0befd0c9175134cdf58fc6a1223a2; Android game branch three-patti-pro-native d53a39d981597ff4eec2a7004a79b457a4a9e7f7. Pages deployment: https://github.com/hozefa111/hh/actions/runs/40071608169 .
