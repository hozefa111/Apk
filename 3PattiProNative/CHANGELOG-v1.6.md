# P0 Foundation — Native 3 Patti PRO v1.6
Date: 2026-10-16. Only `hozefa111/Apk` branch `three-patti-pro-native` changes.

## Implemented
1. In-place round form updates: playing, bid, Hukum, partners, result, score preview; no full-Activity recreate on normal taps.
2. Native bid selector for all 20 allowed bids, as well as ±5 controls.
3. Redraw avoidance when remote rounds or session data change during active round input; rebuild only on player-roster changes.
4. Explicit roundSaving latch; save button locked on confirmation until success/failure callback; failed write preserves input.
5. Firebase write results produce success/failure callbacks; a second write cannot start while one is pending.
6. Player rename uses one atomic Firestore REST commit for player and historical embedded round-name references. Exceeds 500 writes -> fail without mutating anything (requires separate migration plan).
7. Scoring validation checks unique players and partners, min 3 participants, valid bid and role combination.
8. 16 deterministic JUnit test methods including all 20 bid values, both outcomes, invalid cases, immutability and player order.
9. Feature parity matrix checked against the original web source.

## Not yet claimed
- Live Firestore listener parity (current sync still REST polling).
- UI/device smoothness performance on physical phones; no Android instrumented tests yet.
- Stable signed upgrade from previous debug APKs.
- Offline cache, secure credential vault, upload/download backups, all historical share image edge cases.
- Exact screen pixel matching.

IMPORTANT: Writing code or a passing build does NOT prove end-to-end phone behavior; no existing Firebase data was modified by these changes.
