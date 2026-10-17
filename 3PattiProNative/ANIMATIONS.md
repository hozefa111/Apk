# Native animation pass — 2.3.0

This release changes presentation and motion controls, not scoring, game rules or
Firebase game records. Reference functionality remains the 2.2.0 implementation.

## Motion controls

Settings → Animations: **Full / Reduced / Off**. Full is the default. Reduced uses
short fades, immediate numbers and no positional motion, repeated pulses or
confetti. Off applies final states immediately. A disabled Android animator,
transition or window scale overrides Full/Reduced. Haptics remain a separate setting.

The activity owns all custom animators. Pause, destruction and preference changes
cancel them and restore final values, alpha, position and scale. Screen rebuilds
cancel only that screen's motion, preserving an already confirmed result dialog.
Pending pre-draw callbacks are removed on cancellation. Effects are finite and
bounded to 64 simultaneous animators; scrolling never replays card-entry effects.

## Existing screens

- Launch/header reveal and directional tab/page entry; native button/chip feedback.
- Dialog enter/exit transitions preserve existing onShow/onDismiss validation and
  authentication callbacks. Date pickers follow the same motion policy.
- Loading placeholders, refresh rotation, busy progress and validation feedback.
- In-place player/role/result selection, bid counting and progressive form sections.
- Live score counting, signed floating deltas, visible rank-row movement and
  new-leader highlight. Only visible rows animate; actual final points remain the
  accessibility/readable result after completion or cancellation.
- Active-session pulse, confirmed-round checkmark, session winner/top-three reveal,
  award entry and bounded Canvas confetti with a Skip animation control. Reopening
  historical summaries does not replay the celebration.
- Search-result fade without replacing the query field; history/filter transitions;
  analytics bar/line reveal, palette/background transition and share-preview paging.
  Exported PNGs remain static and contain final values.

## Confirmation and reconnect safety

No animation triggers a write, defers a click or changes selection/scoring. Success
feedback comes from the existing successful server-write callback. Unconfirmed
saves retain existing retry/recovery behavior. A bounded private acknowledgement
set suppresses repeated local round celebrations. Results completed while paused
are shown as static confirmations on resume.

Gateway snapshot continuity is presentation metadata only: SDK cache/error,
authentication changes, failed REST reads or a long reconciliation gap reset the
motion baseline. Healthy realtime invalidation still keeps existing write locks.
A coherent no-op snapshot primes the baseline after reconnect/resume without
rebuilding the screen. Backfilled/old/future-invalid rounds and recently restored round IDs do not trigger deltas.

## Verification

83 JVM tests pass locally, including motion policy, overflow-safe counting,
initial-cache/backfill/reconnect/background event guards and existing HH scoring
and session vectors. Debug/sandbox app and instrumentation APK compilation and
lint are run before device CI. New instrumentation exercises actual intermediate
score/rank frames, reconnect suppression, Full/Reduced/Off preference persistence,
Android system animation override, lifecycle cleanup, and skippable session
celebration. The API 26/30/35 matrix enables system animations; a configured test
is not a passed result. Final run results accompany the delivered APK.

No physical-phone performance certification, infinite effects, video exports or
new game features are claimed. Original HH and production Firebase are unchanged.
