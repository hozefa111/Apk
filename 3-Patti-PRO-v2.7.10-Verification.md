# 3 Patti PRO v2.7.10 — Firestore fetching and cache optimization

Signed release verified. Version **2.7.10**, code **31**, same existing application and stable signing certificate. [Source PR #21](https://github.com/hozefa111/Apk/pull/21) is merged into `three-patti-pro-native`.

## Architecture and observed causes

The released Android application is native Java, with Firebase Android SDK authentication and **Cloud Firestore** for the shared tracker. Its card-game screen uses bundled shared web assets and the existing free authoritative Worker; online connection presence uses Firebase **Realtime Database** (`.info/connected` and per-connection presence). Both Firebase services remain in place. The native tracker reads `players`, `rounds` and `sessions`. Scoring, session totals, rankings, analytics and backup/restore use the complete historical record set.

The v2.7.9 native tracker:

- Attached unbounded collection listeners. History displayed 25 rows at a time but initially subscribed to every record.
- Converted every document in all three collections to portable JSON, computed all content-version tokens and reconstructed display records on every coherent listener event, including unchanged metadata.
- Serialized and fsynced the full JSON offline snapshot after each load; that cache was skipped above 8 MB.
- Computed a scoreboard before detecting an unchanged UI refresh.
- Could attach listeners once in `start()` and then immediately remove/re-attach them in the initial authentication callback.

Firestore SDK listeners already send incremental network changes after initial synchronization. The repeated whole-history work described above was **application CPU/storage work**, not evidence that Firestore redownloaded every document for each event.

## Changes and files

| File under `3PattiProNative` | Change |
|---|---|
| `app/src/main/java/com/hozefa/threepattipro/nativeapp/PagedFirestoreCollection.java` | Configurable document-ID cursor listeners, bounded requests, stable ordering, coherent completion, stable non-overlapping logical ID ranges and local-only splits for inserts/deletes. |
| `app/src/main/java/com/hozefa/threepattipro/nativeapp/FirebaseGateway.java` | Background page handling, decoded-record/version reuse, no-op publication suppression, cache coalescing, one initial listener attach, idempotent lifecycle and local numeric timing counters. |
| `app/src/main/java/com/hozefa/threepattipro/nativeapp/TrackerCache.java` | Atomic, app-private SQLite cache writes only changed/deleted rows and saves range boundaries; rejects stale activity flushes and reconciles other local cache writers. No write queue or server mutation. Built-in Android API, no dependency. |
| `app/src/main/java/com/hozefa/threepattipro/nativeapp/DocumentVersion.java` | Direct hex encoding replaces repeated formatter allocation; existing conflict tokens remain byte-compatible. |
| `app/src/main/java/com/hozefa/threepattipro/nativeapp/MainActivity.java` | Lightweight loading progress, avoid an unchanged-data ranking calculation, and Settings → Data sync details. Existing 25-round/20-session incremental rendering remains. |
| `app/build.gradle.kts` | v2.7.10/code31; default150-document pages; override with `-PfirebasePageSize=...`, range2–500. |
| Instrumentation/JVM tests and verification script | Real cursor-boundary CRUD, exact scores, unchanged navigation, cache deltas/rollback/empty snapshots, legacy token compatibility and visible diagnostics. |

Default document-ID ordering uses Firestore's existing index and includes legacy rows missing timestamps. No composite index deployment is required. Mobile Firestore reads complete documents; full fields remain necessary for existing optimistic concurrency, lossless backup, unknown-field preservation and detail views. No field mask or schema change is claimed.

## Caching, synchronization and accuracy

Every query is bounded. Existing cached totals remain available **read-only** while pages load. Partial history is never published as complete or used to enable mutations. All historic rounds still load automatically because current lifetime calculations require them. History and session UI continue to render additional rows on demand.

The existing Firebase SDK persistent cache remains in use. The additional offline tracker cache now stores records in private SQLite and commits changed rows, deletions and a completeness marker atomically. Identical content does not rewrite the cache. Existing JSON snapshots remain a read fallback for upgrades; Firebase requires no migration. An empty completed cache is distinct from a missing cache, preventing cleared rounds from reappearing. Local cache failures cannot mutate remote data or authorize offline writes. Android backup remains disabled as before.

Live cursors remain attached across navigation and short background transitions; existing long-background cleanup is retained. Ranges retain their document-ID boundaries even when a boundary document is deleted. A newly filled discovery page freezes its logical upper bound without rereading that initial page. Overflow splits only the affected range at its midpoint, leaving capacity for future inserts; later ranges do not re-subscribe. A discovery query that starts borrowing from a neighbour after a deletion is bound explicitly once. Boundaries are saved atomically with the cache, so future launches attach bounded ranges directly. Splits/rebounds may reread the affected range, but do not rebuild the rest of history. The default batch size is tunable; pagination bounds individual requests, not total lifetime-history transfer or memory. First-time boundary discovery is sequential; subsequent cached ranges can attach directly.

## Measured results

A reproducible local JVM benchmark used 2,000 synthetic documents, five measured repetitions after warmup, comparing the actual prior and current token implementations:

| Operation | v2.7.9 median | v2.7.10 median |
|---|---:|---:|
| Token calculation for 2,000 records | 175.3 ms | 71.3 ms |

All 10,000 compared tokens matched. These are JVM CPU measurements, **not physical-device or WAN latency measurements**. Raw timings and benchmark sources accompany the report.

## Completed verification

[Final CI run 39914042625](https://github.com/hozefa111/Apk/actions/runs/39914042625) completed successfully on source `295110c3b71e9743f037eaadfed8ee3ae2d7973f`.

| Check | Actual result |
|---|---|
| JVM unit tests | 193 passed; 0 failures, errors or skips |
| Android 11 / API30 instrumentation | 57 passed; 0 failures, errors or skips |
| Android 15 / API35 instrumentation | 20 + 37 passed in two shards; 0 failures, errors or skips |
| Coverage comparison | Same 57 unique instrumentation test names on both Android versions; no duplicates |
| Android lint | 0 errors; 38 warnings remain |
| Signed release / bundled assets | Build passed; stable signer verified; all 16 bundled assets matched |

The emulator integration tests cover bounded cursor CRUD (including deleted boundaries and legacy records missing timestamps), exact score sums, one-record decode/cache updates, unchanged navigation reuse, cache transaction rollback, empty-cache persistence, stale activity flushes, late lifecycle callbacks, existing manual/voice entry and online integration. Destructive tests used Firebase demo emulators only.

With a deliberately small batch of seven, both Android versions observed a maximum batch of seven, 13 attached page queries, 59 decoded records and 225 unchanged-record reuses. The measured edit/delete/insert sequence changed only three cached rows in three transactions. Its first-confirmed-page responses were median/p95 **42/82 ms on API30** and **32/65 ms on API35** (12 samples each). These are Firebase demo-emulator measurements, not production network timings. A single-edit assertion separately verified exactly one record decoded and one cached row written, with no query reattachment.

Earlier checks exposed a cache rollback edge case and callbacks submitted after worker shutdown; both were fixed and covered by passing regression tests. CI emulator graphics instability was resolved using API30 software rendering and API35 Mesa rendering. These settings affect CI only; app graphics settings were not changed. Failed/incomplete intermediate attempts are not counted as passes. The final entire CI matrix is green.

Application/test/build source has not changed since `869201f87edb14c70a4110a46f5baf08639840b5`; subsequent changes only configure CI renderers and a safe diagnostic helper. Final merge: `fff2f7e0f32eb3b5d5d04a932ff90deebb923c6f`.

## Release identity

- Package: `com.hozefa.threepattipro.nativeapp`
- APK SHA256: `aa607cd144c10254df8bcd0f611211152b52b4b0fd50fd6ace93aef604843249`
- Stable signing-certificate SHA256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`
- Previous v2.7.9 release retained. No backend deployment required.
- Physical-device testing and real user WAN measurements have **not** been performed.

## Query diagnostics

**Settings → Data sync details** shows batch size, maximum received batch, query attachments, decode/reuse counts, cache transactions and changed rows, plus median/p95 first-confirmed-page responses. Up to 200 numeric timing samples stay locally in memory. Timings include SDK/cache confirmation/network work, not Firestore execution alone. They contain no player names, document/room IDs, private hands, auth tokens or command payloads and are not uploaded automatically. The existing online **Scores → Check speed** remains unchanged.

## Preservation and practical limits

- Same application/package, stable signing, existing Firebase project, free backend, users, records, formulas, manual/voice entry, online rules and shared assets.
- No Firebase rules, permissions, remote data structures, production records, indexes or Worker deployment changes.
- Destructive tests run only in demo emulators or uniquely named disposable local test databases.
- Complete-history transfer and score/analytics reconstruction remain O(N). Avoiding that dependency would require a separately justified aggregate/change-feed design; this release does not silently truncate history.
- Known Firestore region `nam5` (North America), network distance, service load and free-tier quotas remain potential latency sources. No database migration was performed.
- No physical-device performance claim or universal zero-lag guarantee. The actual user's network breakdown is available through the in-app diagnostics.
