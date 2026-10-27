Faster save and session deletion in the existing 3 Patti PRO app.

- Overlap fresh-token and authoritative admin-role checks safely; retain account, role and record-version validation.
- Reuse history HTTP connections, avoid rebuilding an unchanged verified ledger, and reuse encodings of unchanged records.
- Remove idle delays between step-driven manual/legacy session cleanup requests. Preserve preview, confirmations, backups, cascade deletion and exact score removal.
- Speed up Cloudflare cleanup in bounded slices; skip empty hand/member phases only after transactional verification. Preserve tombstones and pending-export safety.

Controlled Android 11 emulator comparison with 3,000 rounds and 350 ms injected auth/role delays: save acknowledgement 2,291 → 1,135 ms; save-ready 2,802 → 1,345 ms; round deletion 3,212 → 2,310 ms; manual session deletion 8,020 → 2,205 ms. These are local test results, not production-network guarantees.

Focused Android 11/15 authentication, retry, cache-integrity, reconnect and manual/online deletion tests passed. SQLite-backed Cloudflare 4/6-player privacy, outage, restart and deletion tests passed. All 18 bundled game assets are unchanged. Same package and signing identity; no Firebase rules, sync mode, scoring or production-data changes. Physical-phone performance has not been measured.

Verification evidence: [Android 11/15 focused tests](https://github.com/hozefa111/Apk/actions/runs/40479455400), [signed v2.7.24 → v2.7.25 retained-data upgrade](https://github.com/hozefa111/Apk/actions/runs/40481435428), [backend/security/browser regression](https://github.com/hozefa111/hh/actions/runs/40479461193). The browser run's first attempt had an intermittent legacy-room join timeout; the rerun passed. No change to joining is included in this performance release.
