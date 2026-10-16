# Orbit Player Pro — final acceptance gate

This branch is **IN PROGRESS / NOT APPROVED FOR RELEASE**. The user requested no intermediate APK.
The signed v0.3.0 remains the previous public version; do not tag, merge or promote
completion work without the gates below.

## Implemented on this branch (code exists; not all hardware-tested)
- [x] External SRT/WebVTT cue timeline, adjustable offset and font appearance
- [x] Player chapter list extracted from Media3 chapter metadata when present
- [x] Opt-in MediaSessionService background audio and native media controls
- [x] Sleep timer runs inside playback service
- [x] Advanced local search: extension, resolution, duration and file size
- [x] HLS/DASH and direct HTTPS playback by explicit user input
- [x] SMB2/3 browser and random-access SMB DataSource
- [x] Playlists import/export through Android document picker
- [x] Optional open-with-other-player on unsupported decoder error
- [x] Emulator test assertions for Settings access, player launch, bottom control geometry

## Release blockers — DO NOT claim these are finished
- [ ] **Latest exact branch SHA** debug compile AND unit tests pass
- [ ] **Latest exact branch SHA** optimized release/R8 compile pass
- [ ] Latest exact branch SHA emulator playback, Settings and portrait/landscape smoke pass
- [ ] Local Samba integration test passes authentication, directory listing and seekable reads
- [ ] Actual SMB stream plays a real remote MP4/MKV across seeking, pauses and disconnects
- [ ] Background audio session/notification/headphone controls survive app minimize and resume
- [ ] Verify embedded chapters and external subtitle timing across test files; note Media3 limitation for *embedded* subtitle delay
- [ ] Evaluate optional LibVLC support against original lightweight size, memory and thermal requirements; no unsupported format promises
- [ ] Test Android 8 / 13 / 14+ storage permission and media lifecycle
- [ ] Test with 1,000+ videos, media refresh and thumbnail memory/cancellation behavior
- [ ] Check accessibility: buttons, text scaling, TalkBack and reduced motion
- [ ] Update-install signed artifact over existing app, verify all saved folders/playlists/progress survive
- [ ] Final QA signoff without regressions; only then bump version, merge, sign and publish one final APK

## Safety and data
- Keep package `com.orbitplayer.app` and existing signing key.
- Never touch 3 Patti, unrelated Firebase, or user media contents.
- SMB password only lives in process memory; never persist in SharedPreferences or URLs.
- Network URLs accepted only with HTTPS without inline passwords.
- Import/export touches only user-chosen documents, never media files.
- No release while core playback can crash or tests are red.
