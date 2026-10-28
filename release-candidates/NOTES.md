3 Patti PRO v2.7.28 — live connection recovery

- Detects a silent OPEN game WebSocket using its existing ping/pong replies, instead of waiting for the four-minute renewal.
- Gives a CONNECTING socket a bounded 15-second opening window; an attempt without callbacks can no longer block later reconnects.
- Retains a healthy socket during proactive renewal and ignores callbacks from retired sockets.
- Restores the same account, room, seat and private hand. Original pending move IDs are retained for receipt reconciliation. No replacement player or automatic extra move is created.
- Healthy idle connections add no database polling. Server turns, 30-second human timers, bots, scoring, private-card rules, double-tap and 1.5-second card playback are unchanged.

Verified: 129 node tests; six 4/6-seat browser recovery cases; pending/lost-response/token recovery; 22 browser card-input checks; 14 card-playback checks; complete 4-player (one human/three bots) and 6-player (two humans/four bots) hands through real browser controls during a simulated Firestore outage, with all 250 points and no live-move Firestore calls. Android8/11/15 bundled WebView recovery and real-touch tests passed. Android15 required the established awake/focused-window test harness; no card-play logic was changed.

Same official package/signing certificate. Install over v2.7.27 without uninstalling or clearing data. Manual tracker, Incremental Sync, stored rounds, sessions and scores are unchanged. The live Worker and Firebase rules were not redeployed.

Limitations: the originally reported room was stopped before room-specific diagnostics, so its exact trigger is unconfirmed. This release fixes reproduced recovery gaps; it does not guarantee fault-free networks. A genuine participant disconnect still uses the existing pause/admin-resume rules. No physical-phone test is claimed.

Signed upgrade over v2.7.27 passed: cache bytes and marker retained; saved scores, session selection and Big Scoreboard verified. All 16 bundled game assets byte-matched source; test fixture excluded. Hosted web source/runtime byte-matched deployed files.
