Verification candidate for issue #27. This is not production Incremental Sync activation or a claim of real-phone verification. v2.7.20 remains the stable release.

Android changes: wrapped Firestore quota errors retain the persistent cooldown; unenforced journal state falls back to full verified sync. Existing six-command concurrency retries, unique receipts, stale-edit checks, durable SQLite counts/checksum, game rules and online gameplay remain unchanged.

Companion web/backend source PR fixes prepare-mode write preconditions, exact timestamp comparisons and optional Firebase Functions player-registration journaling. Production activation must remain prepare/enforced=false until all gates and separate owner approval.

Automated verification: Android 11/15 tracker and SQLite instrumentation, JVM/lint, web/emulator concurrency and integrity checks, real Chromium IndexedDB/offline recovery, Cloudflare four/six-player outage/restart/private-card checks. This release workflow additionally verifies installation over signed v2.7.20 with marker, actual SQLite cache and session/Big Scoreboard touch checks retained.

Package/signing identity unchanged. Install OVER the existing app; never uninstall or clear data. APK version 2.7.21, code 42. SHA256: 54775ad95d8eff4ee65e667190663a1e457167640dcc31dbca75aa0c7571c792.

Remaining gate: all 5–6 admin phones update over their old app and web editors refresh. Two physical admin phones must concurrently save distinct rounds ONLY in the isolated safe test environment, then confirm matching totals, offline/reopen recovery and retained histories. No physical devices were available in this workspace. Production activation requires separate explicit owner approval after that evidence. No unlimited-free-quota claim.
