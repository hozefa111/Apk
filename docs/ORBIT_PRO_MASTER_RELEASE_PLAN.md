# Orbit Player Pro — Quality-first master release plan

Owner: Orbit Player Pro `com.orbitplayer.app` | Stable baseline: signed v0.3.0
Do NOT repackage as a web app. Preserve signed Android package and saved data.

## Non-negotiable release gates
- [ ] XML resource linking, Kotlin compilation and unit tests pass on Debug AND Release variants.
- [ ] Android emulator smoke test launches library, plays sample content, checks visible controls and screenshot.
- [ ] No regression in opening, resuming, landscape, seeking, captions, folder cards or permissions.
- [ ] Actual Android phone testing (at least Android 8, 13, 14+, with 100/1000 videos), including playback, rotation, long videos and HEVC/MKV samples.
- [ ] Signed APK has matching `com.orbitplayer.app` and incremented versionCode; checksum verified against GitHub Release.
- [ ] Update existing signed APK without uninstall or loss of saved progress.
- [ ] No network permission except user-triggered update; all media managed locally.
- [ ] User can understand limitations; never call untested behaviors verified.

## Milestones — ordered by user impact

### A. Playback core: critical
1. Fix Play All: automatically advance to next video on natural end, but obey Repeat One and ordinary single-video behavior.
2. Preserve queue across Activity recreation/process restarts, handle missing/unreadable next items gracefully.
3. Resolve completed-video history/resume consistency.
4. Correct lifecycle, PiP, rotation and audio focus; investigate subtitle selection and gesture conflicts.

### B. Library and organization: essential
1. True Albums with accurate counts and album thumbnail; folder full relative path, pin/hide/unhide and sort.
2. Favorite individual videos and collection view.
3. Custom named playlists and queue ordering. Support select/multiple-add with one confirmation.
4. History and continue watch, favorites retention, clean-up stale/missing files.
5. Robust storage permission changes, refresh on media change, return to prior folder/scroll.

### C. Playback/accessibility polish
1. Subtitle offset adjustment, styling and external subtitle handling.
2. Preferred audio/subtitles; chapter selection where format provides it.
3. Bottom-only accessible controls, tap targets, labels, talkback, contrast and scaling.
4. Persistent aspect mode, speed, seek interval; high frame rate/hardware playback checks.
5. Search by name, folder, file type, duration, sort and filters.

### D. Power-user features (only once stable)
1. Background playback with MediaSession notification, headset/Bluetooth controls.
2. Network/SMB/NAS folder browsing (optional, explicit permission).
3. Evaluate LibVLC/FFmpeg fallback: measure increased APK size, battery and codecs; do NOT promise universal format support.
4. Playlist import/export, sharing, duplicate filtering and scan exclusions.
5. Optional captions advanced typography and chapter navigation.

## Quality and performance
- Avoid decoding thumbnails on main thread; use bounded caches and cancel stale requests.
- Use diff-driven list updates, retain scroll positions, keep album list responsive for thousands of videos.
- Require no runtime crashes from opening video via both library and ACTION_OPEN_DOCUMENT.
- Explicit error states and recovery buttons; avoid silent failures and fake/non-functional controls.
- Run tests on the *exact* release artifact and real devices before promoting to a stable label.

## Current scope and evidence

The milestone list above is a roadmap, not a statement that every feature is
missing or complete. The [18 October feature audit](ORBIT_RELEASE_AUDIT_2026_10_18.md)
records implementation status, actual run evidence and outstanding verification.
Use the [physical-device acceptance procedure](qa/DEVICE_ACCEPTANCE.md) for checks
that emulators cannot establish. Advanced optional features remain deferred where
necessary for reliability; foreground MediaSession support is not background
service playback.
