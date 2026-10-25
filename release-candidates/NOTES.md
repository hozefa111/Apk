Automatic tracker synchronization in the same 3 Patti PRO app.

- Removed repeated Load complete history prompts. Saved scores appear immediately; verification runs automatically. English last-synced and genuine network/quota status labels.
- Added atomic public change journal, persisted checkpoints and incremental readers for historical additions, edits and deletions. Full lifetime calculations remain derived from complete records; partial pages never become totals.
- Historical cache and checkpoint commit together. Existing identities, scoring, private hands, Cloudflare games and online export/deletion safeguards retained.

IMPORTANT STAGED ROLLOUT: production remains in automatic full-sync fallback (prepare mode, enforcement off) until all admin/editor APKs are updated and old web editing tabs refreshed. Then incremental mode can be safely activated. This deliberate compatibility step means incremental read savings are NOT yet enabled in production. Older manual editors will require an update after enforcement.

Verified: 193 Release JVM tests, real Firestore emulator consistency/security/race cases, browser cache/reload/history updates and Android SDK incremental CRUD/cache/reopen/quota/voice checks. The no-cache quota-screen regression was fixed and its focused Android rerun passed. A final same-certificate upgrade test retains an old-format SQLite cache and verifies visible saved data without the manual Load button.

Local fixture: 1,003 initial records; subsequent unchanged verified reopen uses one tracker-head read and zero ledger documents. Initial bootstrap still reads the full ledger; journal adds two Firestore writes per mutation batch. Excludes rule/auth charges and production traffic. No permanent free-quota guarantee. No physical-device testing.
