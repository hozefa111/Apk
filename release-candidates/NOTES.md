Fixes a false “Please log in before saving changes” error during session deletion while the toolbar still displayed ADMIN.

- Publish UID/token/admin status atomically after role verification, retaining valid status while refresh is pending.
- Revalidate the original account with a refreshed token and server-verified admin role before invoking mutations.
- Block revoked/expired/changed accounts, retain write-in-flight protection, and distinguish connection failures from expired login.
- Preserve deletion preview, both confirmations, local backup, resumable cascade, exact lifetime-score removal and unrelated sessions.

Verification: 8 focused SDK/emulator tests on Android 11 and Android 15 passed, plus 7 cascade service regressions. Covers refresh overlap, double submit, role revocation, expired auth, offline/reconnect, identity retention on gateway reopen, signout, manual/online cascades, repeated delete, recovery archive, six concurrent native commands and stale conflicts. Signed upgrade over v2.7.21 passed without clearing data; cached SQLite contents retained byte-for-byte and score/session screens verified. No physical-device testing.

Package/signing identity, game assets, rules, scoring, production Incremental Sync, journal and production settings are unchanged. No production records were used for destructive testing.

Version: 2.7.23 (44)
SHA256: 88706a3f61b39f44406cbca6756695acb1dc17d467fa4890c7159630e8a4e0c9
