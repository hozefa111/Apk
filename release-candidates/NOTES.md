The same 3 Patti PRO application, package and official signing certificate. Install over v2.7.26 without uninstalling or clearing data.

- Completed manual-session History, awards and summaries now reflect later round corrections and agree with session rankings. Archived metadata stays intact.
- Web manual-save recovery retains the original round and journal receipt after a lost response, including reload. No offline saving or new queue.
- Updated manual-session writers reject stale/concurrent starts. Strong server enforcement is staged: **all admin/editor devices must update to v2.7.27+ and web tabs refresh before owner-approved guard activation**. It is not activated by installing this APK.
- Faster saved-score views and backup validation; analytics calculates off the main thread. Live/session score projections remain authoritative and uncached.
- Existing gameplay, bots, private cards, scoring, timers, double-tap, animations, accounts and data are preserved.

Verified: 213 native JVM tests; 124 web unit tests; 60 Android emulator tests (20 each on Android8/11/15), including touch/double-tap/lobby scrolling; release lint; seven session-guard tests; nine incremental-sync tests; actual browser lost-ack/reload/retry and correction checks; four/six-player SQLite/WebSocket outage/restart/privacy games. Signed upgrade over v2.7.26 retained cache bytes and saved scores. All16 bundled game assets matched synchronized sources.

Measured, 3,000-round Android11 emulator fixture: Home p50/p95 137/170 →7.5/23ms; Big Scoreboard110/123 →3.2/4.2ms; restore validation2106/2184 →259/287ms. Analytics first report ready264ms, rendered off-thread; repeated view render p95 17ms. Corrected session History cold p95 62ms (previous33ms); the initial1255ms regression was fixed. Hosted-runner hardware varies; these are not physical-phone or WAN guarantees. Backup serialization itself was unchanged.

Thirty unchanged web verifications: complete local-cache rewrites30→0 and full-data redraw callbacks30→0; Firestore head reads remain30→30. Local live moves used zero Firestore calls; history exports/setup still use Firestore. No permanent free-quota guarantee. Physical-phone verification was not performed.

Broader web/backend CI40547384604 passed: full browser multiplayer, security, reconnect, controls and backend regressions. Web deployed successfully (40549819758); all five changed client files byte-matched hosted content. Conditional rules deployed; existing Incremental Sync remains incremental/enforced with the same epoch. Strict single-session guard remains owner-gated until editors update.
