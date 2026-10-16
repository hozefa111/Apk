# Native 3 Patti PRO — v2.0 Release Candidate 1
Based on original HH `8b2189d28d7bd9d9c21f509e87bf89717816898f` and Android source branch `three-patti-pro-native`.

## Changes compared with v1.6
- Read-only private offline cache of the last successful players/rounds/sessions snapshot. Cached data is displayed immediately at startup when available; it does NOT queue or submit offline writes.
- Share images now paginate Round, Rankings and Session Summary rather than silently removing late entries from large leaderboards. Android's native share sheet shares all PNG pages together.
- Native PNG renderer scales text to fit narrow rows before using an ellipsis.
- Session Summary now displays native Overview, Awards and full Leaderboard cards in a scrollable dialog.
- Ranks and Player Profile show recent winning-streak badges matching the original HH rules.
- End Session with zero rounds requires explicit confirmation and does not overwrite historical session awards.
- Custom date range now validates start/end order and treats the end date as inclusive through calendar-day arithmetic, including DST changes.
- Player profile rank shows a dash for unranked players rather than falsely showing Rank 1.
- Added a smoke-test workflow that builds the CURRENT source and attempts Android native launch and bottom tab navigation.

## Explicit release limitations
- **Candidate only**: build/unit test passing does not prove 100% web UI parity or real-device smoothness.
- Real-time Firestore listeners are not implemented; current refresh continues ~12-second REST polling.
- No certified offline *editing*; cache is read-only.
- Private release keystore is not configured; debug-built APKs might not update in-place from prior GitHub runs.
- Firebase/Auth device integration test, full long-term offline synchronization, and all screen pixel-diff tests have not been verified.
- The original HH repo and production Firestore data were not modified by this build.
