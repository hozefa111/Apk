# 3 Patti PRO Native — v2.1 Verification Release
Source: `hozefa111/Apk` branch `three-patti-pro-native`.
Web reference: `hozefa111/hh`, commit `8b2189d28d7bd9d9c21f509e87bf89717816898f`.

## Changes since 2.0 RC1
1. Fixed emulator workflow shell command: GitHub's emulator step executes with `sh`, which does not support `set -o pipefail`. Now exercises Home/Ranks/Players/History navigation and checks logcat for fatal exceptions.
2. Admin writes are blocked whenever the latest Firebase collections have not been successfully fetched from the network; offline snapshot is read-only.
3. Three Firestore collections fetch in parallel to reduce time blocked behind serial HTTP requests. Pagination limit refuses to publish partial lists.
4. Round saves use a stable UUID and create precondition. On an ambiguous network error, confirm whether the same document already exists before asking the user to retry. This reduces duplicated Hukum rounds.
5. UI shows LIVE SYNC versus OFFLINE/CONNECTING status.
6. Pure Java RoundAnalytics centralizes win/loss/streak/head-to-head rules; used by Rankings, Player Profile and session badge logic, with 9 additional tests. Scoring still uses the original HH bid rules.
7. The user can retry a failed round save without losing their selections; no production Firebase data was modified by builds/tests.

## Limits of verification
- APK is generated with Android debug signing unless a private release key is separately configured. Update-in-place may fail across builds.
- GitHub compile/unit test and emulator smoke do not certify production sign-in, user-specific Firestore write permissions, performance across all phones or 100% pixel/feature parity with HH.
- Firestore is still polled (~12 s); true native snapshot listeners and background/offline edits are intentionally not represented as complete.
- Existing web app and Firestore records are never mutated by the build or tests.
