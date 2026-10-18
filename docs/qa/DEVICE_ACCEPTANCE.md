# Physical-device release acceptance

This checklist records work that a hosted emulator cannot establish. It is not
completed by compilation, generated metadata, a selected audio track or a first
video frame. Use the exact signed candidate SHA-256 and source commit under test.
Never uninstall a user's existing app or clear its data to make an update pass.

## Record

- Device/model, chipset, RAM, Android/security patch and storage configuration.
- APK SHA-256, signing-certificate SHA-256, versionCode/name and source SHA.
- Library counts, sample codecs/containers, test duration and result/evidence.
- Reproducible steps for failures, crash/ANR logs, screenshots and measurements.

## Update and saved data

1. On signed v0.3.0 create two ordered playlists, favorites, a hidden folder and a
   pinned folder. Play two videos to meaningful unfinished positions. Change list
   preference, seek increment, subtitle size, speed and starting tab.
2. Install the matching-certificate candidate with `adb install -r candidate.apk`.
   Record success without uninstalling or clearing data.
3. Verify order/membership, all saved preferences, history, Continue Watching and
   each actual resumed position. Rename a playlist, remove one entry and restart.
   Original files must remain intact.

## Storage, permission and library

- Test no videos, one video, 100 and 1,000+ real videos; nested paths and identical
  folder names in different internal/SD paths. Verify counts and parent labels.
- Search by title/path, combine format/filter/sort, open/back while scrolled, switch
  grid/list, rotate and reopen after process eviction. Favorites and hidden folders
  must remain consistent in Search and Continue Watching.
- Revoke video access, return to the app, grant full access; on Android 14+ change
  selected access and reselect videos. Collections must survive restricted access.
- Remove/reinsert an SD card; delete/move a file while the app is backgrounded.
  Retry/open through Documents where MediaScanner omits a supported file.
- Record cold/warm startup using `adb shell am start -W -n
  com.orbitplayer.app/.MainActivity`; measure scroll frames with `dumpsys gfxinfo`
  and memory with `dumpsys meminfo com.orbitplayer.app`. Record device/library
  details with measurements. No ANR or reproducible unresponsive taps are acceptable.

## Playback and accessibility

- Exercise transport, dragging, repeat/Play All, missing/corrupt middle and final
  queue entries, end/replay, external share/open and Documents. External open must
  not continue an older library queue.
- Exercise real horizontal/double-tap seek, incremental volume/brightness, pinch,
  hold speed and canceled/multiple touches. Buttons must receive taps reliably.
- Rotate while playing/paused/locked, enter/exit/close PiP, return from Documents,
  turn screen off/on and navigate away/back. Check audio, position, selected tracks,
  timer, controls and system-bar overlap. Verify Back unlock behavior.
- Test wired/Bluetooth play/pause/next/previous, disconnection, competing audio,
  calls/interruptions and recovery. Background service playback is not promised.
- Listen to multiple audio tracks and each device-supported format. Check sync,
  seeking and errors, not just rendered video. Test 1080p/high-resolution, VFR and
  a genuinely long continuous session; the generated one-hour file only validates
  long-duration container startup in CI.
- Check embedded and external SRT/VTT/ASS/SSA, complex styles, real provider grants,
  seek before/after cues, rotation and stop/restart. Track selection must survive.
- Check default/large font, TalkBack, touch exploration, dark/AMOLED appearance,
  denied/empty/error states and every saved preference's behavioral effect.

## Separate reference gate

The original Orbit web source/screenshots/URL is still unidentified. Compare its
actual screens and behavior against the native matrix when available. The
unrelated 3 Patti and loan applications must not be used as Orbit references.

Release sign-off requires results and unresolved issues, rather than checked
boxes without evidence. Codec availability is device-dependent; no universal
codec or full background-service support is advertised.
