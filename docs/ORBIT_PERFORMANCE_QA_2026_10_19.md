# Library responsiveness and recovery QA — 19 October 2026

Baseline `e55a45d`: [build/unit/lint](https://github.com/hozefa111/Apk/actions/runs/39147859303)
and [API 26/33/35 suites](https://github.com/hozefa111/Apk/actions/runs/39147859284)
passed. PR #10 remains open on `orbit-pro-qa-hardening-oct17`.

## Changes under verification

- Filtering captures UI choices on main, then reads preference snapshots and
  playlist JSON on the existing worker. Only Continue Watching queries construct
  the complete resume-position map; ordinary search does not copy it.
- Continue-card selection runs on the existing library worker with immutable
  inputs and generation checks. A single pass prefers the last played accessible
  unfinished video, otherwise the most recently modified unfinished video. Existing
  resume thresholds and preference keys remain unchanged.
- The previous continue card is made unavailable immediately while refreshing;
  hidden-folder actions and scan failures refresh its selection as well. Generation
  checks reject older callbacks and activity destruction cancels pending work.
- Startup errors use real paragraph breaks and actionable file-access guidance
  instead of referring to an error report that may not exist.
- No new dependency, executor pool, permission, application ID or version change.
  Existing asynchronous list diffs and bounded thumbnail decoding are retained.

## Added regression coverage

- Continue selection: preferred item, fallback, hidden folders, missing/deleted
  media, unknown duration, unstarted/completed media and no eligible items.
- Actual Activity/filter/diff/RecyclerView flow with 0, 1, 10, 100 and 1,000
  synthetic metadata entries. Verify counts, end scrolling, search and replacement
  of an older query. Entries share an indexed smoke video with distinct URI query
  keys: this is not a 1,000-file disk/MediaStore scan or a phone speed benchmark.
  The same Activity test checks asynchronous continue-card loading and clearing
  without an older selection becoming clickable again.
- Natural Play All progression past missing and corrupt middle entries; informative
  error at the final corrupt entry; replace that test file with playable bytes and
  activate the real Retry button. Queue entries and files must remain intact.
- Existing generated metadata unit fixtures still cover up to 10,000 entries;
  their elapsed times measure computation in the test runtime, not phone UI speed.

Results will be recorded after tests complete. A passing build or selected audio
track does not establish audible codecs, hardware performance or release readiness.
Physical-device acceptance, long sessions, removable storage and signed upgrade
verification for a new final candidate remain separate gates. Original web source
is still unknown and exact parity remains unverified. No new APK delivery or public
release is part of this QA batch.
