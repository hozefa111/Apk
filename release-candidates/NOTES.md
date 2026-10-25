Same 3 Patti PRO app, package and stable signing certificate. Install over v2.7.15 without clearing data.

- New online rooms use persistent Cloudflare SQLite room actors and private WebSockets. Live moves no longer read/write Firestore; existing Firebase rooms continue on their original transport.
- Firebase history sync uses a durable, idempotent outbox. Completed hands remain saved during quota outages; history/rankings may lag until sync succeeds. Deletion tombstones prevent delayed exports restoring removed points.
- Preserves final bidding choice, bots, 250-point rules, legal-card privacy, double-tap play, ordered computer-card animation and persistent Ready controls.
- Session deletion targets indexed session rounds, with legacy matching preserved. Tracker startup loads a small preview; tap Load complete history to verify the complete ledger before live lifetime/manual operations. Saved totals are labelled and never calculated from partial pages.

Verified: local 4/6-player human/bot hands, active-hand restart, quota outage, reconnect, command replay, rollback routing, private hands and atomic Firestore export/deletion races; responsive browser touch/card/lobby checks. Android 11 SDK/cache/quota/voice instrumentation: 18 passed, 1 backend-dependent case skipped and covered separately. Final candidate gate additionally checks actual bundled WebView touch interactions and update installation over v2.7.15.

Limitations: no physical-phone testing. Firebase Auth, fresh room setup and uncached profile lookup still depend on Firebase. Free Cloudflare limits remain; this is not unlimited hosting or a guarantee against all future quota exhaustion. Local performance measurements are not production latency benchmarks.
