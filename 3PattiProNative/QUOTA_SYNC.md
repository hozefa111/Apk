# Quota-safe sync — v2.3.2

The reported toast was an actual production Firestore `429 RESOURCE_EXHAUSTED` response. A single bounded read-only connection check reproduced it. No production record, rule, index, quota or billing setting was modified.

The previous client used realtime listeners as invalidation signals, reloaded the full three-collection snapshot on metadata/error events, and also polled every minute. This unnecessarily multiplied reads. The new client retains version-checked REST snapshots for write safety, but removes the healthy minute poll and ignores metadata-only listener acknowledgements. Actual document changes still trigger a coalesced snapshot refresh. Explicit refresh is debounced; long-background listeners stop after a 60-second grace period.

HTTP 429 and SDK RESOURCE_EXHAUSTED pause reads/listeners. Exponential cooldown starts at one minute, grows to one hour and respects Retry-After (up to 24 hours). Its deadline/attempt count survive Activity/process recreation. Refresh/resume cannot override it. Offline failures use bounded retry delays. No mutation is queued or automatically replayed. Successful server snapshots restore the normal connection state.

The app displays a persistent connection explanation, next retry time and read-only cached table. A fresh install without a cache displays a connection error rather than an endless loading skeleton or a fabricated empty table. Scoring, optimistic write preconditions, recovery journals and pending round IDs are unchanged.

An exhausted server-side daily quota still requires its reset or an owner billing/quota change; this APK cannot increase or bypass that limit. Do not clear app storage to address a server quota error, because that removes the local offline snapshot.

Verification includes eight JVM retry-policy cases; synthetic Android 429/Retry-After, refresh/resume spam, process-equivalent gateway recreation, blocked mutations, cache preservation, recovery, a 63-second healthy idle read-count check, and no-cache/cached error UI screenshots. Existing safety, parity, animation, dialog and export coverage remains enabled. All integration tests use demo-three-patti; release navigation smoke is offline. Final results accompany the APK.
