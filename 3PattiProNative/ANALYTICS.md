# Advanced point-game analytics — v2.4.0

Open More → Analytics. Choose a date range, session scope, player and analytics
view. All computations run on the device from the SDK's saved table snapshots.
There are no extra Firebase reads, analytics writes or remote AI requests.
The original web repository, production records/rules and scoring rules are unchanged.

## Views

- Overview retains player totals, average/highest bid and Hukum win rate.
- Player comparison shows totals, played counts, points per played round,
  outcome-based win rates, same-side/opposing counts and a shared score timeline.
- Role performance separates Hukum, Partner and Opponent rounds and denominators.
- Bid analysis uses only the selected player's Hukum calls, showing round count,
  wins/losses, rate, net points, gains and losses for each saved bid.
- Partnership chemistry counts co-participating Hukum-team pairs, combines their
  saved deltas and shows sample counts/rates. Opponent-side co-participation is
  not called a partnership. Pairs with 10 decided rounds sort before small samples.
- Form supports last 5/10/20 played rounds and the preceding non-overlapping
  window. It shows current/best winning and losing streaks and population standard
  deviation of round points. Partial windows are explicitly identified.
- Comeback/lead tracking rebuilds chronological competition ranks. A comeback is
  the largest prior deficit later recovered to a tied or outright lead; an
  incomplete recovery is not called a comeback. Lead is measured over the second
  player. Tied leader-set changes count; the initial leader is not a change.
- Session comparison has two selectable sessions, totals, per-player averages
  and differences. Today/month/custom and current/previous/named session scopes
  can be combined. Partial-session results are labeled as filtered.
- Round impact orders rounds by total rank-place movement of existing players,
  then absolute point changes. New participants are entries, not rank jumps.
  Detail actions open the existing read-only round dialog.
- Personal records show actual highest/lowest round deltas (including all-negative
  histories), best scoped session, longest outcome streaks, highest lead and comeback.
- Auto insights state actual recent-vs-previous averages, streaks, role/pair sample
  counts and recovered deficits. They use deterministic rules, not predictions.

Score charts have two labeled colors, a zero baseline, exact point/date readout,
tap/keyboard inspection and Previous/Next round controls. Drawing/page/score
animations respect the existing full/reduced/off preferences. Player, rival,
window, session scope, section and session choices survive Activity recreation.
Long lists paginate without limiting calculations. Analytics share images use the
actual filtered report and 64-bit totals, including historical players.

## Counting and data boundaries

Settlements and practice-marked (`type`, `mode`, `source`, `practice`, `isPractice`)
rounds are excluded. No computer-practice store is read or merged into this report.
This release does not introduce a new computer game or practice history store.

Participants come from `playerIds`; duplicate IDs count once. Score deltas come
from saved `scoreChanges` and are not recalculated with current scoring rules.
Zero points do not identify a win. Hukum/Partner wins follow the saved Hukum
result; Opponents win when Hukum loses. Unknown/malformed results are excluded
from win-rate denominators and break streaks. Absences are skipped. Missing
point entries contribute zero. Fewer than 10 played/decided rounds are labeled
Limited data; this threshold is a caution, not a statistical confidence guarantee.

Only observed participants enter reconstructed leaderboards, so never-played
roster players do not become imaginary leaders on zero points. Ties share
competition rank (1,1,3). Totals/ranks start at zero at the selected filter's start;
this is a filtered history, not an assertion about an earlier lifetime lead.
Removed players retain historical names when available.

Explicit session IDs take priority. Legacy rounds without IDs use start-inclusive,
end-exclusive boundaries; overlapping matches choose the latest start. Unknown
IDs and undated/unmatched rounds are retained as separate or Unassigned groups.
A table that has not loaded is shown as unavailable, not as an empty history.
Date filters use the device timezone and calendar boundaries, including DST and
inclusive custom end dates. Analytics supports end-only custom ranges; it does
not inherit the legacy web's unbounded end-only behavior. Undated rounds appear
only in unbounded analytics. Session/day scopes can therefore yield no data.

No production write test or physical handset test is part of the emulator suite.
Final counts, CI source and signed APK identity are recorded in the release report.
