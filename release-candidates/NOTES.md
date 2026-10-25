Ranks and Big Scoreboard now offer Lifetime and Sessions. Choose a named session from a clean dropdown, with active sessions first and a green LIVE badge when verified online. Lifetime remains all-time; Profile and History date filters are unchanged.

Selected live Cloudflare sessions use authoritative session totals, including hands pending Firestore export. Totals replace saved history rather than being added to it. Completed manual, legacy Firebase and Cloudflare sessions use saved history when complete. Session selection carries between Ranks and Big Scoreboard. Deleted sessions clear gracefully.

The new read-only score channel exposes no cards or hidden partnerships and never changes player presence. Only the selected live/pending session is subscribed; completed cached sessions need no listener. No new history scans or polling loops.

Verification: 203 distinct JVM tests across full/focused runs; 3 Android 11 emulator UI tests; actual 4/6-player SQLite/WebSocket actor tests covering reconnect/restart, private projections, pending export totals and deletion. Signed APK certificate, package/version, all 15 bundled web asset hashes and native session classes verified. Publication is gated on updating v2.7.17 in place, retaining a file marker/old SQLite cache and touching the signed release's session selector and Big Scoreboard.

Package and stable signing certificate are unchanged. Existing accounts, rooms, scores and game rules are preserved. No production records were created/deleted for tests. Physical-phone testing was not performed. Live online scores require the existing room participant identity; completed saved tracker history remains available offline.

Sources: hozefa111/Apk@3f8516a (three-patti-pro-native); hozefa111/hh@cf2dfbd. Backend deployed before APK publication. APK v2.7.18, version code 39.
