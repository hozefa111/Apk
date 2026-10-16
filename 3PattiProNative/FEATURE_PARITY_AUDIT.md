# 3 Patti PRO: HH web ↔ Native parity audit
Baseline: `hozefa111/hh` main @ `8b2189d28d7bd9d9c21f509e87bf89717816898f`
Native branch: `hozefa111/Apk` / `three-patti-pro-native`
Last audited: 2026-10-16.

Meaning: ✅ implemented in native (not a claim of device verification); ⚠️ partial; ❌ missing; 🛡 deliberately unavailable until a backup/safety workflow exists.

| Area | Original web behavior | Native state / remaining issue | Priority |
|---|---|---|---|
| Admin login/logout | Firebase Auth; read-only visitors | ⚠️ REST Auth implemented; live-session logout/login flow needs device testing | P0 |
| Database | Firestore onSnapshot realtime | ⚠️ REST poll each ~12 seconds; listeners/offline cache missing | P0 |
| Table Status | Players, rounds, positive balance, top winner | ✅ arithmetic matched; visual/interactive polish remains | P1 |
| Quick actions | New round, Players, Ranks, History | ✅ present as native touch targets | P1 |
| Sessions | Start, optional name, end, no-round confirmation | ⚠️ native start/end and summary; no-round end differs, timer missing | P0 |
| Player roster | Active/benched list, add/edit/bench/restore/remove | ⚠️ operations native; grouped layout, validation and accessibility incomplete | P1 |
| Round form | Playing, bid 155-250 step 5, Hukum, partners, result | ✅ key features; v1.6 in-place selection updates to fix redraw | P0 |
| Score preview | Hukum +2bid/-bid; partners +bid/-round(bid/2); others 0/+bid | ✅ same rule; v1.6 exhaustive bid tests | P0 |
| Save round | Confirm + write then share | ⚠️ confirmation and callback native, v1.6 locks inflight save; network-timeout idempotency still needed | P0 |
| Rankings | Date filters, medals, roles, streak badges | ⚠️ date filters, rankings, profiles present; streak/medal visuals incomplete | P1 |
| Custom date | Inclusive selected date range | ⚠️ native picker; DST and inverted-range validation need testing | P1 |
| Player profile | rank, W/L, role stats, head-to-head, streak, best/worst, round history | ⚠️ several stats implemented, rich layout/popups lack exact parity | P1 |
| Round history | full partners/opponents and score-change chips; share/delete | ⚠️ compact native card; detail requires extra tap and some fields differ | P1 |
| Session history | editable names, badges, MVP, leaderboard, duration, share | ⚠️ native basic summary + image, detailed sheet missing | P1 |
| Share round | Canvas screenshot of HTML share card | ⚠️ native PNG custom layout; long text, cutoffs, gallery save | P1 |
| Share ranks | full statistics, medals and filter | ⚠️ native PNG; truncates after 65 players | P1 |
| Share session | awards, six badge types, leaderboard | ⚠️ native PNG; names and multi-page overflow not handled | P1 |
| Rename data safety | player + batch update embedded names | ✅ native v1.6 atomic commit <=500 write documents, else reject before changes | P0 |
| Delete protection | admin confirmations | ⚠️ native confirmations; archival/restore policy missing | P0 |
| Clear All | two confirmations + typed `CONFIRM` destructive action | 🛡 omitted deliberately pending backup + audit + restore safeguards | P0 |
| localStorage migration | web migration banner and upload into Firestore | ❌ Android native cannot silently read browser localStorage; opt-in export/import only | P2 |
| APK updates | web auto-deploy vs Android signing | ❌ native uses ephemeral GitHub Actions debug key; stable signing needed | P0 |
| Device automation | website runs in browser | ❌ last emulator flow outdated and not a proof of current install/UI | P0 |

## Release gates
- **P0**: Safe write + retry, stable login state, scoring vectors, atomic rename, full round form responsiveness, current APK smoke tests on emulator/device.
- **P1**: Side-by-side visual screenshots for each web view, exact profile/session popups, proper all-data image sharing, real-time listeners and offline cache.
- **P2**: Non-breaking optional extras only after parity: analytics/charts, export, card templates, player search, win-streak trends, session calendar.
- Tests must not modify the user's production Firestore: use mocked data, Firebase Emulator Suite or a completely separate test project.
- Original HH code and existing Firestore data are read-only throughout implementation.
