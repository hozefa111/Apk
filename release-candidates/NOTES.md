Fixes the stale “Saved scores · synchronizing” banner on Home after verified LIVE SYNC.

- Sync status and Last synced timestamp update automatically, without changing tabs.
- Update only the status area; preserve Home content and scroll position.
- Retain offline/quota warnings and cached-score protection.

Verification: focused Android 11 and 15 regression tests and lint passed. Signed upgrade over v2.7.21 verified without clearing data, preserving SQLite cache and existing score screens. Same package and signing certificate. Game assets, rules, Incremental Sync, journal, cache integrity and production settings are unchanged. No physical-device testing was performed.

Version: 2.7.22 (43).
APK SHA256: 88b4a11afbc543faaab58e67a2cda721ac8a81baaacb04f156e1df14859d1e67
