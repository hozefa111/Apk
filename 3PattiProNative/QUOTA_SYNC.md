# Direct SDK sync — v2.3.3

The native client previously received HTTP 429 through its REST data path. This
establishes a failed request, not which project quota was exhausted. A working
web screen may be live or cached; it is not proof either way. No project billing
or quota change is assumed necessary without the actual backend error details.

Players, rounds and sessions now come directly from three Firestore SDK realtime
listeners, like the web app. A change updates the existing listener snapshot;
it does not trigger a REST collection scan. There is no REST endpoint or HTTP
client in FirebaseGateway, including writes. Manual refresh republishes the
current listener state and starts missing listeners, rather than rereading all
three collections. Healthy listeners perform no periodic query reload.

Only three server-confirmed snapshots without pending local writes enable edits.
Cache/pending events switch the table to read-only. The last complete saved table
remains visible offline; a fresh device sees an explicit connection state. SDK
reconnect metadata resumes live state automatically. Long-background listeners
stop after a grace period. Old REST-specific cooldowns are removed on upgrade;
a real SDK RESOURCE_EXHAUSTED error retains its own persistent bounded cooldown.
Permission failures identify access/account configuration, separately from limits.

All creates, updates, deletes and recovery batches use SDK transactions. Each
transaction reads every target before any write. Creates require absence;
updates/deletes compare a deterministic token of all typed document fields.
The SDK then protects those reads against changes during commit/retry. Tokens
are stored in the internal `_updateTime`/archive `updateTime` slot for compatibility,
but are `sdk1:` content fingerprints, not Firestore timestamps, and are never sent
as REST preconditions. This detects changed contents, not an intervening edit that
returned the entire document to identical contents. Transactions fail offline;
no batch/offline write queue is introduced. Stable round IDs still handle ambiguous
commits without creating duplicates. Server timestamps and literal field names
are preserved, along with bytes, references, locations and nanosecond timestamps
in portable backups. Cross-project references fail closed on restore.

Bulk operations retain their private journal, preflight, backup and initiating
account requirement. Each batch is atomic; an entire multi-batch operation is not.
Older timestamp-based pending delete/rename plans cannot be safely verified by
SDK content tokens. They fail closed and retain their journal/recovery backup;
export recovery before explicitly stopping/replanning them in Settings. Restore
plans and already-applied equivalent rename records still reconcile by contents.

Verification uses only the demo-three-patti Auth/Firestore emulators. It includes
external edits, stale dialog conflicts, duplicate round detection, 501-record
rename/clear/restore, interrupted commit recovery, portable field fidelity,
legacy cooldown migration, SDK quota/restart/recovery, offline UI and the existing
parity, animation, navigation, dialog and share tests. Signed production APK UI
smoke is offline. No production data/rules/billing or original web files change.
Emulator success does not establish production quota availability or physical
handset/network behavior; those are separate from the verified client behavior.
