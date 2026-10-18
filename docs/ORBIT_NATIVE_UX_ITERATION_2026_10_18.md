# Native UX development iteration — 18 October 2026

Baseline: `10a0fab` on `orbit-pro-qa-hardening-oct17`, PR #10. Its build and
API 26/33/35 checks passed. The earlier signed v0.3.1 candidate remains the last
verified signed artifact; its evidence does not automatically verify these edits.

The user approved starting the native improvement plan and confirmed that the
original web URL/source is unknown. Native design work proceeds independently;
exact web parity is unverified and is not claimed.

## Implemented in this iteration

| Area | Native behavior | Files |
|---|---|---|
| Settings navigation | Sticky Back/Search toolbar; category filters; instant search over existing views; no-match state; query/category/scroll restoration | SettingsActivity |
| Working preferences | Existing keys/actions retained; selected values rebuild without losing filtering; AMOLED updates Settings surfaces immediately | SettingsActivity, OrbitSettings unchanged |
| Playing queue | Current item/count, Play now, move up/down, remove upcoming items, auto-next switch; changes persist without editing playlists or files | PlaybackQueue, PlaybackQueueDialog, PlayerActivity, queue XML |
| Queue lifecycle | Queue modal and scroll restore on recreation; resizes on orientation changes; dialogs close on stop; auto-hide respects open modals | PlayerActivity, PlaybackQueueDialog |
| Library cards | Responsive 16:9 previews, circular overflow feedback with 48dp targets, readable metadata, large-font grid adaptation | VideoPreviewLayout, row_video, row_folder, MainActivity |
| Small details | Singular/plural video counts; locale-independent format labels; gesture single-tap uses standard performClick | strings, VideoAdapter, PlayerGestureZone |
| Playback UI | Queue context replaces small branding text; More sheet remains scrollable and rows grow for larger text; bottom transport preserved | activity_player, orbit_pro_sheet, styles |

## Verification required

- Compile debug/optimized release and instrumentation APKs; both unit/lint variants.
- Existing 8 instrumentation tests plus 4 additions: queue persistence/playlist
  isolation, actual queue UI + edited-order natural progression, Settings search
  and real toggle persistence, Settings category restoration/sticky navigation.
- Existing native navigation, denied/partial permission and generated codec suites
  on API 26, 33 and 35.
- Inspect actual Settings, search, folder/video card and queue screenshots; check
  narrow width, landscape and increased font size.

At initial implementation, build/resource contracts passed and local device tests
were running. Final results must be appended before claiming this iteration is
verified. No production release or new APK delivery is authorized by this record.

## Boundaries

Android 8+, native Views/Media3, application ID, signing configuration and existing
preference keys are retained. No new runtime dependency, codec fallback, network
browser or background playback service is introduced. The active video is kept
in the queue so previous/next and completion remain anchored. Removing/reordering
queue entries never deletes files or changes a saved playlist.

Phone performance/audio/SD-card/long-session acceptance remains outstanding.
Advanced subtitle delay/appearance, chapter support and service/network features
remain later plan items; they are not represented by decorative controls.

## First regression findings

At `ca02783`, [build/unit/lint](https://github.com/hozefa111/Apk/actions/runs/39145506143)
passed, and the local API 26 emulator passed all 12 instrumentation tests.
[Expanded CI](https://github.com/hozefa111/Apk/actions/runs/39145506122) passed
API 26, but API 33/35 failed the new queue-recreation assertion. Native UI,
permissions and media matrices still completed with exit 0. The queue dialog
was missing after recreation: modern Android can call state saving after
onStop has dismissed it. The fix caches the open/scroll state before dismissal
and uses it when saving. This run is not represented as fully passing.

Actual keyboard screenshots also showed excessive chrome above Settings search
results on a narrow window. Search now replaces the toolbar/categories, keeps its
query across recreation and offers an IME Search action to hide the keyboard.
Closing search restores category navigation. Fresh cross-version results are
required for these fixes.

The local software emulator logged System UI and media-process ANRs during boot
at 16:27/16:28 UTC, before Orbit's first process startup at 16:30 UTC. The stale
system dialog blocked manual UI taps; diagnostics were retained before choosing
Wait. Clean Settings screenshots were then captured. This is recorded as an
environment limitation, not evidence of phone responsiveness or an ANR-free OS.
