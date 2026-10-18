# Additional API 26 emulator evidence

Application source: `3515424`. Local x86_64 Android 8 emulator, 320dp width,
software acceleration. These checks supplement CI; they are not physical-phone
performance or complete provider/PiP compatibility results.

## Real document picker with broad storage access revoked

1. Revoked `READ_EXTERNAL_STORAGE`; package dump showed an empty runtime-permission
   section. Opened MainActivity and tapped the real Open a video button.
2. In Android DocumentsUI, selected `orbit_smoke.mp4` from Recent. PlayerActivity
   opened and logged `FIRST_FRAME` and `STATE_READY` without a player/fatal error.
3. Confirmed saved URI was
   `content://com.android.providers.media.documents/document/video%3A15`.
   ActivityManager showed the media-documents provider grant targeting
   `com.orbitplayer.app`, with `persisted=0x1` (read).
4. Force-stopped Orbit, revoked broad access again, then opened that persisted
   document URI. New process 3976 logged `FIRST_FRAME` and `STATE_READY` without
   errors. Its grant remained `persisted=0x1`, with `owned=0x0`, confirming the
   persisted grant rather than a live picker activity was sufficient.
5. Restored broad storage permission after the test.

This verifies one actual API 26 media-documents-provider round trip and restart,
not every Android version/provider or external subtitle provider.

## Media replay and PiP lifecycle

- At the end of the clip, dispatched Android media Play (key 126). The active
  Orbit platform MediaSession reported state 3 (playing), position 0. This checks
  end replay through the platform route; Bluetooth/wired hardware remains untested.
- Entered PiP using the real player button. ActivityManager recorded stack 4 and
  `mLastReportedPictureInPictureMode=true`.
- Moved the pinned task to fullscreen using Android's task command. ActivityManager
  reported the player resumed with PiP mode false. The captured UI restored title,
  PiP/More buttons and bottom transport, showing the ended clip at 00:28.
- Entered PiP again, then removed the pinned stack through Android's task command.
  ActivityManager returned to Launcher and MediaSession reported its media-button
  session null, with no Orbit session left. No PLAYER_ERROR or FATAL EXCEPTION
  appeared in the process log.

This is one emulator cycle, including an ended/paused frame. It does not establish
playing/locked PiP stress behavior, notification playback, interruptions or long
sessions on phones. No production user data or external messages were involved.
