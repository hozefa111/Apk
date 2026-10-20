# Royal Scoreboard — v2.6.1

Home has a Royal Top3 podium; Ranks adds the podium, detailed player cards and a
Big Scoreboard view. Lifetime is the default. Current Session uses the existing
analytics session attribution; Ranks date filters intersect the selected scope.
Big Scoreboard inherits that scope/range and restores Ranks scroll on Back/Close.
It hides app chrome, supports portrait/landscape and has a temporary screen-on
switch initialized from the existing preference. Exit restores the prior policy;
pause clears the override. No active session means an explicit empty scope.

The read-only ScoreboardModel uses saved deltas and64-bit sums, excludes practice
and settlements, and computes competition ranks(1,1,3). Equal scores have joint
rank labels and gold crowns for joint leaders. The podium shows three players in
stable roster order, with a link/count for additional players tied at the cutoff.
Narrow/large-font layouts stack cards; normal-width layouts place #1 in the center
when three players are present. All names wrap. Cards show total, personal last
played-round delta, current-session points and gap to leader. An explicit zero is
a real zero round; absence does not replace the player's last played round.

The existing MotionEvents continuity gate permits animation only for a confirmed
fresh foreground round. Full mode reconciles score counts and X/Y card movement,
with a shared crown anchor and a short gold highlight when leaders change. Reduced
mode uses a120ms score fade; Off presents final values directly. Temporary rank
movement badges last5s and clear on navigation/pause. Reconnect, reopen, filters
and history backfill do not replay celebrations. No new save/reveal sequence was
added. Existing manual/voice forms and save/retry mechanisms are retained.

All views reuse existing in-memory SDK snapshots, without new Firebase queries,
new data schemas or writes. Share Rankings reads the same selected rows/totals.
ScoreRules/FirebaseGateway, original HH and production data/rules/billing are
unchanged. v2.5.0 source c60e8c14f6a99ce9a96b210ba49e63547fd7724d and its stable
signed APK are retained for a higher-version UI rollback if requested. Installing
an older versionCode directly is not an in-place rollback; do not uninstall or
clear app data to revert.

Tests cover tied/negative/large totals, chronology and absences, scope boundaries
via reused analytics tests, practice exclusion, actual animation frames, full/
reduced/off and reconnect suppression, native Home/Ranks, Big Scoreboard lifecycle,
large-font landscape and screen-on cleanup. CI uses isolated Firebase emulators;
physical handset/live-production behavior is outside those checks.

## Visual refinement (2.6.1)

The podium now sits on a connected dark stage, with metal-coloured gradient
surfaces, initial badges and a drawn gold crown that does not depend on emoji
fonts. Podium height follows its contents; there is no fixed empty column.
Scope/date controls use compact typography while preserving 48dp touch targets.
Score breakdown rows place name and total together, then separate personal last
round, current session and leader gap. Large text and narrow screens stack rows.
Exact signed totals auto-size within the podium, including 64-bit values.
The animation anchors, continuity gate and Full/Reduced/Off behavior are retained.
Additional emulator cases verify actual text layout bounds, compact podium height,
profile access and long names/positive/negative 64-bit scores; preview fixtures
are synthetic and never touch production.

## Big Scoreboard presentation (2.6.2)

Big mode uses a dedicated gold leader hero (including joint leaders), drawn crown,
large exact signed total and two labelled personal-round/session metric cells.
Other players use compact rank/score panels with their played count, personal last
round, session and leader gap retained. Top controls use a labelled accessible
48dp close control and a small screen-on action beside live/offline status.
Long names and scores wrap, and font-scale/landscape text bounds are checked.
Existing animation anchors, scope rules and save/sync behavior are unchanged.
