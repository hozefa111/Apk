# Orbit Player Pro release audit — 18 October 2026

Follow-up work is tracked in [the native UX iteration](ORBIT_NATIVE_UX_ITERATION_2026_10_18.md)
and [19 October performance/recovery QA](ORBIT_PERFORMANCE_QA_2026_10_19.md).
The signed-candidate results below identify the earlier tested source; they do not
implicitly verify subsequent Settings/queue/UI edits. The user confirmed that the
original web reference is unknown; native work proceeds with parity unclaimed.

## Baseline and scope

Audited branch: `orbit-pro-qa-hardening-oct17`, clean at
`91d2b7257c06632ba3cca9e7d4244c5b0883360e` on takeover. Package
`com.orbitplayer.app`, minSdk 26, development 0.3.1 / 14 are preserved.
PR #10 is open, unmerged, with no discussion comments or review threads.
Its 19 changed files and complete diff were inspected alongside all 15 Kotlin
sources, manifest, layouts, drawable/values resources, tests, scripts and workflows.
The baseline build and emulator workflows reported success at that exact SHA:
[build](https://github.com/hozefa111/Apk/actions/runs/39022559909),
[API 26/33/35 smoke](https://github.com/hozefa111/Apk/actions/runs/39022559856).
These are historical baseline results, not results for subsequent edits.

PR #9 (`orbit-pro-completion-v040`, `7e45fa8`) contains divergent, explicitly
unreleased background/subtitle/network experiments. It is not merged wholesale:
doing so would replace the verified foreground playback lifecycle and expand
dependencies without current verification. The unrelated 3 Patti branches are
untouched.

## Web reference provenance

No Orbit web source or original screenshots were supplied in this conversation.
Accessible account repositories were listed. Source inspection of `hh` identifies
3 Patti, and `studio` and `hozefa` identify loan applications. Orbit branch history
contains native source, not an identifiable original web player. Those applications
are not design references for Orbit. Every web-parity cell below is **unknown**;
no visual or functional parity is claimed. An original URL/reference is requested.

## Feature matrix

“Source present” establishes implementation only. A test listed below is required
evidence, not a claim that it passed. P0 blocks core usability; P1 is core polish;
P2 is optional scope. Web behavior is unknown for every row.

| Features | Initial state | Native expectation / issue | Priority | Files | Verification needed |
|---|---|---|---|---|---|
| All videos; grid/list | Source present, baseline smoke | Stored list preference must apply on first launch; no adapter reset during refresh | P0 | MainActivity, VideoAdapter | Launch directly into list; refresh and preserve scroll |
| Actual folders; nested folders; accurate counts | Source present, baseline smoke | Full identity, single tap, real count; no cross-volume merging | P0 | VideoRepository, FolderAlbum, FolderAdapter | Nested/same-named internal + SD-card fixtures |
| Folder paths; back; scroll preservation | Partial | Show parent path; explicit back; restore Albums scroll after opening folder | P1 | MainActivity, row_folder | Scroll/open/back, rotation, process recreation |
| Folder pin/hide/unhide; Play All | Source present | Preferences persist; hidden media excluded everywhere; folder queue isolated | P0 | MainActivity, OrbitSettings, SettingsActivity | Overflow/persistence/hide/continue-card regression |
| Search by file/folder; sort; format | Source present, baseline smoke | Debounced computation off UI thread; correct newest/name/size/duration and filters | P0 | MainActivity, FormatUtils | Deterministic 0/1/10/100/1,000/10,000 metadata fixtures |
| Duration filtering | Missing | Evaluate only after core fixes; duration sort already exists | P2 | MainActivity | Document remaining scope |
| Favorites; recent; history; Continue Watching | Source present | Favorite state visible; history and resume clear independently; hidden items excluded | P1 | VideoAdapter, MainActivity, SettingsActivity | Persistence, completion, clear history/positions separately |
| Playlists create/delete/add; multi-add; ordered playback | Source present | No deletion of media; stable insertion order | P0 | OrbitCollections, MainActivity | Duplicate names/items, update/process persistence |
| Playlist rename; remove individual item | Missing UI (remove storage method exists) | Accessible contextual actions; atomic collision-safe rename | P1 | OrbitCollections, MainActivity | Rename collision, blank name, order, remove, reopen |
| Multi-selection | Partial, modal selection exists | Add to playlist / play selection; no original-file mutation | P1 | MainActivity | Select/deselect, empty selection, 1,000-item dialog |
| Missing/deleted media; auto-refresh; permission changes | Source present | Forget stale accessible rows, retain saved collections | P0 | MainActivity, VideoRepository | Revoke/reselect Android 14 partial access; remove file while backgrounded |
| Storage selection | Partial | All accessible MediaStore volumes + document picker; no tree scanner | P1 | VideoRepository, MainActivity | SD card identity; picker without library permission |
| Local open; external ACTION_VIEW; document picker | Source present, baseline smoke for first two | External file must never inherit library auto-next queue | P0 | MainActivity, PlayerActivity, PlaybackQueue | Isolated queue; revoked grant; picker provider latency |
| Play/pause; previous/next; relative seek; seekbar | Source present | Buffering pause intent and replay at end; disabled unavailable queue actions | P0 | PlayerActivity | Buffer/play/pause/end/replay, first/last queue entry |
| Play All; repeat one; auto-next; unreadable items | Source present, navigation unit tests | Natural end respects repeat; bounded forward error recovery | P0 | PlaybackQueue, PlayerActivity | Real two-file transition, missing/corrupt middle/last entry |
| Save/restore progress; playback completion | Source present | Resume active session exactly; completion removes saved progress | P0 | PlayerActivity, MainActivity | Near-end background; short clips; updates preserve keys |
| Aspect; rotation; PiP; speed | Source present, rotation baseline smoke | Bottom transport; no chrome in PiP; state survives lifecycle | P0 | PlayerActivity, activity_player | Portrait/landscape/PiP enter/exit/close, paused state |
| Audio selection; embedded subtitles; external subtitles | Source present, media verification outstanding | Tracks persist across background/picker; size preference works | P0 | PlayerActivity, OrbitSettings | Multi-audio and SRT/VTT/ASS/SSA; rotate/recreate/stop/start |
| Sleep timer | Source present | Pauses at deadline; show and clear timer accurately | P1 | PlayerActivity | Expiry while active/background/PiP; recreation |
| Tap/double-tap; seek swipe; volume/brightness; pinch; hold | Source present, baseline single-tap smoke | Accumulate small volume deltas; cancel seek on pinch; buttons receive taps | P0 | PlayerGestureZone, PlayerActivity | Real touch gestures, small increments, cancel, multi-touch |
| Lock mode | Source present, inaccessible in compact shortcuts | Lock reachable in landscape; touch lock survives recreation | P0 | PlayerActivity, orbit_pro_sheet | Landscape lock/unlock, rotate locked, accessibility |
| Headset/Bluetooth; audio focus; interruptions | Foreground platform session present | No background service claim; pause on noisy; focus handled by ExoPlayer | P0 | PlayerActivity | Media keys, disconnect, competing player, incoming call on device |
| Errors / unsupported codecs | Source present, generic dismiss-only error | Clear recovery: retry / next / close; no crash or silent success | P0 | PlayerActivity | Corrupt/deleted/unsupported samples |
| Settings; appearance; privacy; updates/about | Source present | Every control acts; accessible labels; edge-to-edge safe insets | P1 | SettingsActivity, UpdateManager, layouts | Persist each setting, narrow width/large font/TalkBack, update failure |
| Subtitle offset / appearance beyond size / automatic matching | Deferred | Evaluate existing PR #9 separately; never fake controls | P2 | PlayerActivity / PR #9 | Subtitle timing and SAF grant test before inclusion |
| Chapters | Deferred | Require real metadata and format fixtures | P2 | PR #9 | Chapter parsing/navigation against known timestamps |
| Background service + notification | Deferred | Dedicated MediaSessionService lifecycle required | P2 | PR #9 | Foreground service restrictions/API 35, notification, stop/restart |
| Playlist import/export; duplicates; folder exclusions | Deferred (hide exists) | Preserve order and URI privacy; distinguish hide from scan exclusion | P2 | OrbitCollections, VideoRepository | Roundtrip/dead URI/exclusion test before inclusion |
| SMB/NAS; broader codec fallback | Deferred | Size/licensing/battery/security/device support must justify dependencies | P2 | PR #9 / future | Real NAS and codec matrix; no universal codec claim |

## Release gates still requiring evidence

- Local baseline build, then debug + optimized release compilation, both unit-test variants and Android lint on the final source.
- Fresh API 26/33/35 emulator regression results for the final commit; historical runs do not qualify.
- Actual visual inspection and navigation tests at narrow width, landscape and larger fonts.
- Real codec/subtitle matrix, device playback/interruptions, large-library and long-session performance.
- Same-certificate signed v0.3.0 in-place upgrade and preserved playlists/favorites/resume.
- Original web reference for parity. No final release until the unresolved gates are reported accurately.

No interim APK is delivered, no production signing identity is generated or replaced,
and no stable release is published by this audit.

## Local verification log

- Java 17 / Gradle 8.13 / SDK 36 installed in the managed workspace. Unchanged baseline debug + optimized release + JUnit build passed (`docs/qa/baseline-build.log`, 4m22s).
- Baseline visual inspection on a 320dp-wide API 26 emulator reproduced a wrapped brand title and automatic search keyboard which left no folder cards visible. The unchanged baseline smoke consequently failed its folder visibility assertion at this narrow size; this does not contradict the previous Pixel 2 CI result at its original configuration. The repaired UI removes the extra header action and prevents unsolicited keyboard focus.
- First full lint pass found 19 errors: one API 27 theme attribute in unqualified resources and 18 Media3 API opt-in uses. They are addressed with API-compatible resources and an explicit Media3 opt-in. Subsequent lint must run on frozen source to provide final evidence.
- Metadata fixtures validate counts, hidden folders, file/folder search, combined filters, all sort modes, pinned folders, playlist/history ordering, missing URI handling and resume boundaries at 0/1/10/100/1,000/10,000 entries. Observed host computation for 1,000 entries was ~12 ms, and ~12 ms for 10,000 after warm-up. These are computation fixtures, not Android UI/device benchmarks.
- Generated containers are independently probed with ffprobe: H.264/AAC MP4, H.264 MKV, HEVC MP4/MKV, VP9/Opus WebM, H.264/AC3 MKV, 1080p H.264, one-hour H.264, variable frame rate, no audio, multi-audio + embedded SRT. Corrupt media and external SRT/VTT/ASS/SSA fixtures are separate. Generation alone is not playback evidence.

## Expanded CI findings (development commit 2a822f7)

- [Build, both unit variants and both lint variants](https://github.com/hozefa111/Apk/actions/runs/39096267939): **passed**. Each unit variant ran 24 tests; lint reported zero errors, with existing warnings retained for review rather than suppressed.
- [Expanded API 26/33/35 suite](https://github.com/hozefa111/Apk/actions/runs/39096267901): **failed**, correctly blocking promotion. Library/player smoke and denied/partial permission recovery passed. All three jobs reported a rendered external-caption assertion failure (SRT on API 35; ASS on APIs 26/33), after most other instrumentation passed. Media compatibility execution did not run after this failing step. This run must not be presented as green.
- Follow-up work gives an explicitly selected external subtitle a labelled track override, and makes the test wait for the new subtitle track instead of accepting READY from the previous media-item generation. Current-source reruns are required before calling this resolved.
- A short-window layout audit also identified excessive library chrome in landscape. Responsive compact folder/video rows, a focused Search presentation and a real filters dialog preserve browsing space; a new instrumentation check measures the available browser height and opens a folder.

## Update identity inspection

Initial inspection before internal signed QA (final outcome below):

The downloaded public v0.3.0 APK matches its published SHA-256 checksum:
`5679e15fbaaecbc52895906609b1322009dbf9b420ae95e9a0a320f767d549d1`.
APK metadata confirms `com.orbitplayer.app`, versionCode 13 / 0.3.0.
Its signing certificate SHA-256 is
`27d8f4fd282016b5829348caace9ffb47dbd36f3ea1cfd7819569708274fbda2`.
Development remains versionCode 14 / 0.3.1. This verifies the stable artifact's
identity only; a matching signed candidate and an actual in-place update are
still required. The existing production private key is not available locally.

## Repair status and verification boundaries

The following updates supersede the **initial state** column above; unverified
scenarios remain release work, rather than being implied by source presence.

| Area | Implementation status | Available evidence | Still unverified |
|---|---|---|---|
| Library query/search/sort/counts | Immutable query engine on worker; cancellation rejects stale results | 10 engine unit tests, fixtures through 10,000 entries; source review | Physical 1,000-video scrolling, startup/memory/battery measurements |
| Thumbnails | Shared 12 MiB cache; two workers; bounded queue of 64; recycled requests canceled | Build/lint and source review | Physical decoding latency and many corrupt thumbnails |
| Folder/gallery/list navigation | Parent/storage paths, explicit back, scroll restore, square covers; compact landscape rows/filters | Native smoke; landscape browser-height/open/back instrumentation on API 26/33/35 | Removable SD-card insertion/removal, TalkBack, large-font visual review |
| Favorites/playlists | Visible favorite badge; create validation; atomic rename; individual removal; ordered persistence | Three device persistence tests using isolated actual SharedPreferences/JSON, API 26/33/35 | Physical signed-update migration until signed job evidence exists |
| Permissions | Full/denied/partial reconciliation; guarded observer; preserved collections | Denied UI on 26/33/35, partial recovery UI on 35 | Real user selection/reselection in Android permission picker |
| Player lifecycle/transport | Pause intent, live position vs completed progress, recreated tracks/lock/timer; released-player callback ownership guard | Four player instrumentation tests + native UI smoke on 26/33/35 | Long sessions, real interruptions, PiP stress, hardware media keys |
| External captions | Explicit labelled selection; Media3 eager SubtitleExtractor merged with video; no new parser/codec dependency | All SRT/VTT/ASS/SSA fixtures produce actual cues after stopped-picker return, on API 26/33/35 at `06cac51` | Real SAF provider grants, timing/style fidelity of complex ASS files |
| Errors | Retry/next/close; sidecar failure can recover without subtitles; external queue isolated | Queue and lifecycle tests; codec/corrupt matrix execution tracked separately | Real unsupported hardware decoders, audio audibility |
| Settings/updates | Real saved preferences; accessible targets/insets; exact Orbit APK asset chosen in shared repository | Persistence instrumentation, native Settings navigation, layout contracts | Every preference's real-phone behavior, update download/install user journey |
| Original web parity | Search completed across accessible repository references; no identifiable Orbit source | Native/web provenance documented above | Original source/screenshots/URL required for verified parity |
| Advanced optional scope | Deferred as listed in initial matrix | No new unstable services/network/codecs included | Separate design, implementation and device QA |

### External-caption regression resolution

The selected-track override alone did not fix every run. Media3's default lazy
sidecar preparation could leave the explicitly chosen track without rendered cues
after stopped-picker return and a paused seek. `LocalMediaSource` now uses Media3's
standard eager subtitle extractor; parsing/I/O stays on the loader thread.
[Build at `06cac51`](https://github.com/hozefa111/Apk/actions/runs/39097679724)
passed both build, unit and lint variants.
[Emulator run at that SHA](https://github.com/hozefa111/Apk/actions/runs/39097679773)
passed native UI, permissions and **all eight instrumentation tests on each API**.
The overall run remained red: Gradle uninstalled the test target before the new
independent media suite launched. This is a harness failure, not completed codec
verification. `c489613` makes that suite install/grant its own target and retain
useful launch diagnostics. Fresh complete results are required.

### Internal signed-upgrade procedure

The emulator workflow has an opt-in `verify_signed_upgrade` dispatch input.
It depends on all three smoke jobs, builds an optimized signed candidate using
existing private Actions secrets, checks its certificate against v0.3.0, verifies
the downloaded stable checksum, and performs `adb install -r` on a disposable
API 26 emulator. Stable playback writes actual history/progress; deterministic
legacy-format favorites/playlist fixtures supplement that state. The check
compares data across installation and verifies optimized playback resumes and
advances. Fixtures are identified explicitly: this does not establish real-user
or physical-device migration. Missing signing secrets fail the job.

This workflow uploads an **internal** candidate only after success. It has read-only
repository permission and no GitHub Release publication step. It must not be
confused with dispatching the existing publishing workflow.

### Short portrait visual review and scanner limitation

A 320dp-wide API 26 visual review confirmed readable folder paths/counts and
accessible Settings, but exposed excessive chrome in short portrait video grids.
Windows below 600dp now use a smaller header, Search through the real Search tab,
and a filters/sort menu, while keeping the stored gallery/list choice. The row
presentation threshold remains below 430dp, independently of compact chrome.
Folder/video/Settings screenshots are captured by native smoke for review.

At `c489613`, the complete API 33 and API 35 suite passed. API 26 passed all
instrumentation and its media cases through H.264/AC3, then exposed a platform
MediaScanner omission for HEVC MKV. The harness now records `mediastore_indexed`
and `source_uri_scheme`, attempting direct file playback for unindexed HEVC
fixtures. Common formats still require MediaStore indexing. This documents a
library discovery limitation; it does not silently equate scanner visibility with
codec support. Each sample result is saved immediately, including before a later
failure. Final-source reruns remain required after the short-window refinement.

## Frozen-source automated results — `3515424`

[Build/unit/lint run](https://github.com/hozefa111/Apk/actions/runs/39113956703):
**passed**. Debug and optimized release compile; instrumentation APK compiles;
24 unit tests per variant (48 total), zero failures/skips. Both lint variants
have **zero errors and 181 warnings**, mainly hardcoded text/internationalization,
small text, unused resources and platform recommendations. Warnings are retained,
not hidden. Local frozen-source build also passed (2m31s).

[Emulator and internal upgrade run](https://github.com/hozefa111/Apk/actions/runs/39113956324):
all **three API smoke jobs passed**, with each suite summary recording exit 0 for
native UI, permission recovery, instrumentation and media matrix. Each API ran
eight instrumentation tests with zero failures/errors/skips. The signed upgrade
job is tracked separately below; three green smoke jobs alone do not mean that
job or physical acceptance passed.

The same source additionally passed the native UI smoke locally on an API 26
software emulator at 320dp width, using the compact menu's real File type and Sort
videos actions. Captured folder/video/Settings/landscape-player screens were
visually inspected. These are emulator inspections, not frame-time benchmarks.

### Generated media compatibility evidence

`R` = first video frame rendered, app remained alive and no fatal exception was
observed. `E` = informative player error, app remained alive. This is limited
startup/format evidence from these emulator decoders.

| Fixture | API 26 | API 33 | API 35 | Interpretation |
|---|---|---|---|---|
| H.264/AAC MP4 | R | R | R | AAC track selected; audibility unverified |
| HEVC/AAC MP4 | R | R | R | Emulator decoder result, not every phone |
| H.264/AAC MKV | R | R | R | AAC selected |
| HEVC/AAC MKV | R | R | R | API 26 omitted MediaStore indexing; direct file open rendered |
| VP9/Opus WebM | R | R | R | Opus selected; audibility unverified |
| H.264 without audio | R | R | R | No selected audio expected |
| H.264/AC3 MKV | R | R | R | **No AC3 audio track selected**; video success does not promise sound |
| 1080p H.264 | R | R | R | Startup only; sustained smoothness unmeasured |
| One-hour H.264 container | R | R | R | Long-duration metadata/startup only; not a one-hour session |
| Variable-frame-rate H.264 | R | R | R | Startup only; full sync/seek acceptance remains |
| Two-audio + embedded-SRT MKV | R | R | R | Default AAC selected; actual switching/embedded timing still needs acceptance |
| Corrupt MP4 | E | E | E | Handled error; no crash |

Actual external SRT/VTT/ASS/SSA cue assertions passed after stopped-picker return
and paused seek on all three APIs. Complex styling, long captions and actual
provider grant behavior still require device checks. No universal codec support,
AC3 fallback, audible output or sustained playback performance is inferred.

### Remaining release gates

- Physical low/mid-range phone performance, real 1,000-video libraries, long
  sessions, removable storage, audible tracks, interruptions, real Documents
  grants, large fonts and TalkBack: use [device acceptance](qa/DEVICE_ACCEPTANCE.md).
- Original Orbit web reference and verified parity: still unavailable. Additional
  accessible `Excel`, `gym`, `loan`, `loan1` references are unrelated savings,
  fitness and loan projects; no Orbit source was identified. `myapp` is empty.

The application source is frozen at `3515424`; the `52fc70d` upgrade-harness
correction does not alter that app tree. Verification-document updates also do
not alter the tested APK implementation. Keep
PR #10 unmerged and the stable release unchanged while release gates remain open.

### Additional local Android behavior evidence

[API 26 manual evidence](qa/API26_MANUAL_EVIDENCE.md) records actual DocumentsUI
selection with broad storage access revoked, a persisted media-documents read
grant, and successful rendering again after force-stop/reopen without broad
access. It also records platform media Play replaying an ended clip, PiP entry,
fullscreen return with restored controls, and dismissal releasing the platform
media session without player/fatal errors. These close specific emulator gaps;
other providers, phone hardware and stress cases remain unverified.

### Signed-check harness correction

At `3515424`, the internal signing job built with the production certificate,
installed over stable v0.3.0 and compared preserved collections/progress/history,
then failed on the emulator's rejected `logcat -c` command before optimized
playback verification. This was not counted as a passing upgrade job.
`52fc70d` changes only upgrade diagnostics and CI acceleration: log clearing is
best-effort and candidate diagnostics use its fresh process ID. Application code
remains identical to `3515424`. The corrected run is
[39114910814](https://github.com/hozefa111/Apk/actions/runs/39114910814), which
subsequently passed in full, including its signed-upgrade job.


## Final internal candidate verification — `52fc70d`

[Build, both unit variants and lint](https://github.com/hozefa111/Apk/actions/runs/39114913781): **passed**.
[PR API 26/33/35 suite](https://github.com/hozefa111/Apk/actions/runs/39114913871): **passed**.
[Dispatched full suite and signed upgrade](https://github.com/hozefa111/Apk/actions/runs/39114910814): **passed**, all four jobs.

The signed v0.3.1 / code 14 candidate installed over signed stable v0.3.0 / code 13
without uninstalling. Legacy-format favorites/playlist fixtures and real stable
playback history/progress were unchanged across installation; the optimized
candidate resumed playback and advanced beyond that saved position. The actual
release screenshot was inspected and shows rendered video. The
[stored result](qa/signed-upgrade-result.json) explicitly identifies physical
verification as false.

The downloaded internal artifact was independently verified with `apksigner`:
certificate SHA-256 matches stable
`27d8f4fd282016b5829348caace9ffb47dbd36f3ea1cfd7819569708274fbda2`.
Candidate APK size: **1,141,085 bytes**. Candidate APK SHA-256:
`1a7cdf822642330d64abd692a460ae76cb62fda564820264d991120bd28e7806`.
This identifies an internal QA artifact, not a published production release.
The private key was never downloaded, generated, changed or committed.

Application tree `e3f630540848fc0a207612fc54db5df684317000` is identical at
`3515424` and `52fc70d`. Remaining changes in this handover are documentation only.
No main-branch merge, GitHub Release publication or interim APK delivery was made.
Physical-device acceptance and the missing original web reference remain open;
advanced deferred scope remains as described in the feature matrix.
