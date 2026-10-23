# 3 Patti PRO v2.7.9 — latency diagnosis and optimization

Same application: `com.hozefa.threepattipro.nativeapp`, version **2.7.9**, code **30**, with the existing stable release signing certificate.

## Findings and changes

1. **Shared request queue:** all rooms previously waited behind authentication, database and presence I/O in a single coordinator queue. Authentication now runs outside room queues, and authoritative room mutations use independent per-room serialization.
2. **Shared timer completion:** waiting for every room's background task could delay another room's subsequent automatic turns. Room tasks now run independently with at most four concurrent background jobs, a persisted alarm watchdog, and per-room retry backoff. The tests require two successive automatic turns in a second room while the first room is still blocked.
3. **Database network work:** a normal move previously needed separate begin/read/commit calls. Transaction start and canonical-room/receipt reads now share one Firestore batchGet call, followed by commit. Transaction conflicts retry atomically; missing transaction tokens fail closed. History/deletion retain their established transaction interface.
4. **Geography:** read-only production database metadata confirms Firestore location **nam5 (North America)**. Cross-region distance can contribute latency for players in India. No database/project migration was performed; no production WAN breakdown is inferred from emulator timings.
5. **Phone feedback:** pending moves retain Scores/History/diagnostic access. Lost confirmations retry the exact original command ID/body, so a second action cannot be invented. Deleted/revoked rooms and account changes clear obsolete local retries.

## Diagnostics and privacy

Open **Check connection speed** on the online home, or **Scores → Check speed** during a game. The English dialog shows median/p95 timing breakdowns and has **Copy report**. It holds at most 200 samples in memory on that page; it sends no diagnostic report automatically.

Server-Timing separates queue, authentication, transaction/read, commit, presence and total request work. The client measures token acquisition, request round trip, matching-or-newer authoritative snapshot arrival and a next-frame opportunity. Hidden/background frames are excluded. Snapshot-before-response and coalesced snapshots are supported. Next-frame timing is not physical-display FPS.

Exported samples contain no player/room IDs, names, private cards, login tokens, command bodies or credential values. Server account disable/revocation checks still run on every authenticated request. Rate counters and one-use exchanges are atomic; server credential refresh is single-flight.

## Controlled before/after evidence

An isolated actual Worker benchmark compares the v2.7.8 global coordinator with the optimized coordinator, using the same current adapters and mocked external endpoints, warmed credentials/JWK, and an injected **200 ms authentication delay in request A**. Ten unrelated request-B samples per case:

| Request B | Median | p95 |
|---|---:|---:|
| v2.7.8 global queue | 214.0 ms | 252.0 ms |
| v2.7.9 isolation | 7.9 ms | 29.7 ms |

This demonstrates removal of cross-request waiting in controlled conditions. It does **not** establish the same improvement factor to real-phone games or real Firebase/network latency. The raw report and reproducible isolated benchmark are included in the evidence ZIP.

## Actual verification

- [Web/backend CI](https://github.com/hozefa111/hh/actions/runs/39713944411): **72 rules/service tests**, **7 security tests**, **95 browser checks**, and **24 Worker/Firestore integration tests** passed. The preliminary unit-only Worker run skipped three emulator-dependent cases; the final emulator run executed all 24 with zero skips.
- [Android integration CI](https://github.com/hozefa111/Apk/actions/runs/39827633225): **Android 11/API30: 51/51**, **Android 15/API35: 51/51**, zero failures/errors/skips. These are emulator instrumentation tests of the sandbox variant, including embedded WebView and existing tracker flows. The signed production-configured Release APK is built separately from the same application source.
- Earlier Android 15 attempts in run 39712873665 were not successful: one lost its emulator and another lost window focus and was cancelled at the job timeout. They are not counted as passes. The later successful run above used identical application/test source. Subsequent commits only improve the CI device wake settings, live diagnostics and npm installation bounds; no app behavior changed.
- Shared assets in the APK match the tested web source (15 files plus manifest). Git diff confirms that changes after the passing Android source affect only the CI workflow and verification shell script.

Local signed Release build passed, including **192 JVM tests**, zero failures/errors/skips, and lint with **zero errors and 38 warnings**. Package, version and stable certificate are verified. The APK is verified as version 2.7.9/code30, package `com.hozefa.threepattipro.nativeapp`.

## Deployed source and download integrity

- Web tested source: `594112eb8b9b1f56794650871efe2fcebcbb3107`; merged through [web PR14](https://github.com/hozefa111/hh/pull/14) as `abcf286738b02c81dcec1d1b9b0f193d968f7119`.
- Android tested application source: `e3ca96afdfd467d7470f7b2b57cc735b8fdda927`; merged through [native PR20](https://github.com/hozefa111/Apk/pull/20) as `74a53ff22bbc4fabeb0899f097c5995e869aba1a`. Latest branch head `85ab50c8d61d008916b27486db13f3bb3ba1fe37` differs only in CI/harness files.
- Free Worker deployed version: `c1063b72-17d1-4b59-8fa1-eb231b1d0fb8`. Existing Durable Object binding, canonical Firestore data and rules retained. Production smoke confirmed OPTIONS 204, unauthenticated requests rejected, and timing/CORS headers present. Hosted web client/runtime/CSS match tested source.
- Previous Worker version, for rollback reference: `43605f86-c7df-453f-a00e-815af5110725`.
- APK SHA-256: `fe44e9e9ac10e18eb7dbf4859e2b42c4505aadafac5f11dc80a94ac81c5372bf`.
- Stable signing certificate SHA-256: `dc0697a03526612bc54c3d9ad0b4a10e9ed9b3b6ad22e4e53269f904a72ba9b4`.

All requested automated release gates passed for this application source. Physical-phone performance remains unverified.

## Preservation and limitations

- Existing free Cloudflare/Firebase setup, authentication and unified human scoring retained. No paid hosting, new auth system or database migration.
- Family rules, 30-second human turns, 900 ms bot thinking, trick collection animation, manual/voice entry and existing history remain unchanged.
- Destructive fixtures run only in demo emulators. No QA users/rounds were created in production.
- No physical-phone performance test was available. Internet/service latency and free-tier quotas remain practical limits; zero-lag performance is not guaranteed.
