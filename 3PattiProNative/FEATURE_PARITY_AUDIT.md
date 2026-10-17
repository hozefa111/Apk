# HH web ↔ native parity audit — 2.2 development

Reference: HH `8b2189d28d7bd9d9c21f509e87bf89717816898f`.
This table describes implementation, not a claim of complete device verification.
See [verification record and release gates](VERIFICATION.md).

| Area | Native work in this iteration | Remaining verification/limit |
|---|---|---|
| Login/logout | Auth SDK persistence; immediate logout write lock; login validation | Production Android config/account validation |
| Firebase | Native snapshot subscriptions; debounced full REST reconciliation; watchdog; private offline snapshot | Cross-device latency and disconnect tests |
| Safe writes | Server timestamps, version preconditions using displayed record versions, no offline write queue | Device tests; no production mutation used |
| Round retry | Durable full payload/UUID, locked unconfirmed selections, Settings recovery, payload-checked reconciliation | Process death/network fault device coverage |
| Scoring | Original formula, all bids, role combinations and results | 840 original-web vectors; no differences in 324 production records |
| Dashboard/session | Table totals, active timer, empty-session confirmation, summary after save | Device/visual review |
| Players | Active/benched grouping/filter, validation, edit/bench/restore/remove, recoverable history rename | Multi-batch interruption/concurrent-change review |
| Ranks/profile | Stable creation-order ties, medals, roles, streaks, win rate, best/worst, H2H, complete role histories | Visual and accessibility review |
| Date filters | Sunday week; bounded month/year; inclusive custom days; DST-safe | Unit tests include 23/25-hour days |
| Round/session history | Opponents, score deltas, durations and detailed summaries | Visual review and interaction matrix |
| Session awards | HH zero-score hot-streak behavior; six badge types | Differential session-summary/device review |
| Sharing | Round/rank/session PNGs, pages, wrapped award values, bounded preview, palettes, gallery/folder save | Real share targets and extreme names review |
| Backup/restore | Typed JSON archive; restore missing records; preflight conflicts; recovery archives and replay journal | 16 MB archive limit; large restores use recoverable batches |
| Clear All | Saved current backup required; two confirmations and typed CONFIRM; players retained | Recoverable batches; completed batches persist if interrupted |
| Legacy migration | Explicit import of HH 3PattiProState JSON, stable IDs, empty-roster guard | Cannot silently access browser localStorage |
| Settings | Appearance, compact history, haptics, keep-awake, account and backup controls | Device review |
| Search | Player, round, bid/result/date and session search | Large-history performance review |
| Analytics | Date filters, bid/Hukum metrics, player score bars and cumulative trends | Device/visual review |
| Share Studio | Palette selection, filtered rankings, round/session selection and preview | Device/visual review |
| Notifications | HH notification helper integration is not certified | Registered Android Firebase config and FCM device test unavailable |
| Signing | Stable release signing hooks and fail-closed check | Stable key unavailable; v2.1 update compatibility unverified |

Builds/tests never write production Firebase. HH repository remains unchanged.
100% parity and a final release are explicitly withheld until remaining gates pass.
