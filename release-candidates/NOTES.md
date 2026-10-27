Reduces the session leaderboard opening delay without changing game rules or scoring.

- Selected-session score connections no longer wait behind slow session-list requests.
- Reuse a healthy selected connection for 15 seconds of in-app navigation; backgrounding disconnects immediately.
- Cancel superseded tickets/sockets, retain account/session isolation, and avoid redraws for revision-only frames.
- Coalesce warm catalog requests for 30 seconds; always refresh catalog after backgrounding. Older pages remain on demand.

Controlled emulator measurements (3,000 local rounds; 3,500 ms delayed catalog): first verified live-score frame 4,519 ms before → 281 ms after (~94% reduction). Completed cached-session frames: 125–138 ms. Ranks/Home/Big Scoreboard reuse used one ticket; genuine background/resume established a new connection. These are local test measurements with animations disabled, not physical-phone or production-radio latency guarantees.

Checks: six focused instrumentation cases on Android 11 and 15 plus targeted score-model unit tests passed. A final Android 11 case verifies no repeated warm catalog calls and fresh lookup after background. Signed upgrade over v2.7.23 preserves existing app data and byte-identical SQLite cache. Same package/signing certificate; all bundled game assets unchanged. No production Firebase settings, journal, cache integrity, accounts, scores or Cloudflare game state changed. Physical phones were not tested.

Version: 2.7.24 (45).
APK SHA256: bb369d26433b9c5c9b7f07d9e9e8ccdb0dea00f987f4d757b8714d52116de61a
