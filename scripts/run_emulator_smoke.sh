#!/usr/bin/env bash
# Native playback smoke test. Fails on a real regression, uploads useful diagnostics.
set -euo pipefail
PACKAGE=com.orbitplayer.app
mkdir -p qa
collect_diagnostics() {
  adb logcat -d -v time > qa/emulator-logcat.txt 2>/dev/null || true
  adb shell dumpsys activity activities > qa/activity-state.txt 2>/dev/null || true
  adb shell dumpsys package "$PACKAGE" > qa/package-state.txt 2>/dev/null || true
  adb exec-out screencap -p > qa/screenshot.png 2>/dev/null || true
}
trap collect_diagnostics EXIT

adb wait-for-device
for attempt in $(seq 1 60); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then break; fi
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || { echo "Emulator did not finish booting"; exit 1; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if (( API >= 33 )); then
  adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_VIDEO
else
  adb shell pm grant "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE
fi

# sys.boot_completed can precede emulated shared-storage availability (API 35).
# Retry the actual fixture write, not just mkdir/test -w, which may succeed before
# the FUSE mount is ready. Exhaustion remains a hard setup failure with diagnostics.
orbit_fixture_ready=false
for attempt in $(seq 1 30); do
  if adb shell mkdir -p /sdcard/Movies &&
      adb push smoke.mp4 /sdcard/Movies/orbit_smoke.mp4; then
    orbit_fixture_ready=true
    break
  fi
  echo "Shared storage not ready for smoke fixture (attempt $attempt/30)"
  sleep 2
done
$orbit_fixture_ready || { echo "Could not seed smoke video in shared storage"; exit 1; }
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Movies/orbit_smoke.mp4 >/dev/null || true
ID=""
for attempt in $(seq 1 25); do
  adb shell content query --uri content://media/external/video/media \
    --projection _id:_display_name > qa/media-rows.txt
  ID="$(grep -F '_display_name=orbit_smoke.mp4' qa/media-rows.txt | sed -nE 's/.*_id=([0-9]+).*/\1/p' | head -n1 || true)"
  [[ -n "$ID" ]] && break
  sleep 2
done
[[ -n "$ID" ]] || { echo "Media scanner never indexed smoke.mp4"; exit 1; }
URI="content://media/external/video/media/$ID"

adb logcat -c || true
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee qa/library-launch.txt
sleep 3
python3 scripts/tap_android_view.py "$PACKAGE:id/btnSettings"
sleep 1
adb shell dumpsys activity activities > qa/settings-activity.txt
grep -F "$PACKAGE/.SettingsActivity" qa/settings-activity.txt >/dev/null || {
  echo "Settings was not opened by tapping the Settings control"; exit 1;
}
adb exec-out screencap -p > qa/settings-portrait.png
python3 scripts/tap_android_view.py "desc:Back to library"
sleep 1

# Verify the full user journey: MediaStore -> Folders -> folder videos -> tap -> READY.
# Opening the Player through ACTION_VIEW alone cannot catch a broken gallery adapter.
wait_for_ui() {
  local view_id="$1"
  for attempt in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/orbit-gallery.xml >/dev/null 2>&1 || true
    adb exec-out cat /sdcard/orbit-gallery.xml > qa/gallery.xml 2>/dev/null || true
    if grep -F "resource-id=\"$view_id\"" qa/gallery.xml >/dev/null; then return 0; fi
    sleep 1
  done
  echo "Gallery view was not found: $view_id"
  return 1
}
wait_for_ui "$PACKAGE:id/folderTitle"
wait_for_ui "$PACKAGE:id/folderMore"
adb exec-out screencap -p > qa/folders-portrait.png
python3 scripts/tap_android_view.py "$PACKAGE:id/folderMore"
adb shell uiautomator dump /sdcard/orbit-folder-options.xml >/dev/null
adb exec-out cat /sdcard/orbit-folder-options.xml > qa/folder-options.xml
grep -F 'text="Play all videos"' qa/folder-options.xml >/dev/null || {
  echo "Folder overflow menu did not expose working actions"; exit 1;
}
adb shell input keyevent 4
python3 scripts/tap_android_view.py "$PACKAGE:id/folderTitle"
wait_for_ui "$PACKAGE:id/title"
wait_for_ui "$PACKAGE:id/moreOptions"
adb exec-out screencap -p > qa/videos-portrait.png
python3 scripts/tap_android_view.py "$PACKAGE:id/moreOptions"
adb shell uiautomator dump /sdcard/orbit-video-options.xml >/dev/null
adb exec-out cat /sdcard/orbit-video-options.xml > qa/video-options.xml
grep -F 'text="Add to playlist"' qa/video-options.xml >/dev/null || {
  echo "Video overflow menu did not expose playlist actions"; exit 1;
}
adb shell input keyevent 4
# Exercise the real file type and video duration sort selectors.
adb shell uiautomator dump /sdcard/orbit-controls.xml >/dev/null
adb exec-out cat /sdcard/orbit-controls.xml > qa/library-controls.xml
ORBIT_COMPACT=false
if grep -F "resource-id=\"$PACKAGE:id/formatFilter\"" qa/library-controls.xml >/dev/null; then
  python3 scripts/tap_android_view.py "$PACKAGE:id/formatFilter"
else
  ORBIT_COMPACT=true
  python3 scripts/tap_android_view.py "$PACKAGE:id/btnFilters"
  python3 scripts/tap_android_view.py "text:File type"
fi
adb shell uiautomator dump /sdcard/orbit-formats.xml >/dev/null
adb exec-out cat /sdcard/orbit-formats.xml > qa/format-choices.xml
grep -F 'text="MP4"' qa/format-choices.xml >/dev/null || {
  echo "Library format filter did not discover the actual MP4 video"; exit 1;
}
adb shell input keyevent 4
if $ORBIT_COMPACT; then
  python3 scripts/tap_android_view.py "$PACKAGE:id/btnFilters"
  python3 scripts/tap_android_view.py "text:Sort videos"
else
  python3 scripts/tap_android_view.py "$PACKAGE:id/btnSort"
fi
adb shell uiautomator dump /sdcard/orbit-sorts.xml >/dev/null
adb exec-out cat /sdcard/orbit-sorts.xml > qa/sort-choices.xml
grep -F 'text="Longest video first"' qa/sort-choices.xml >/dev/null || {
  echo "Library duration sort is unavailable"; exit 1;
}
adb shell input keyevent 4
python3 scripts/tap_android_view.py "$PACKAGE:id/title"
for attempt in $(seq 1 25); do
  adb logcat -d -v brief > qa/gallery-player-logcat.txt
  if grep -E 'OrbitPlayerQA.*STATE_READY' qa/gallery-player-logcat.txt >/dev/null; then break; fi
  if grep -E 'FATAL EXCEPTION|OrbitPlayerQA.*PLAYER_ERROR' qa/gallery-player-logcat.txt >/dev/null; then
    echo "Gallery tap opened a broken video"; exit 1
  fi
  sleep 2
done
grep -E 'OrbitPlayerQA.*STATE_READY' qa/gallery-player-logcat.txt >/dev/null || {
  echo "Tapping a library video did not start native playback"; exit 1;
}
adb shell dumpsys activity activities > qa/gallery-player-activity.txt
grep -F "$PACKAGE/.PlayerActivity" qa/gallery-player-activity.txt >/dev/null || {
  echo "Tapping a gallery thumbnail did not open PlayerActivity"; exit 1;
}

# Clear the first session's READY evidence before evaluating the external route.
adb logcat -c || true
# Exercise exported ACTION_VIEW route (required by Android 13+ intent-filter enforcement).
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.DEFAULT -d "$URI" -t video/mp4 -n "$PACKAGE/.PlayerActivity" | tee qa/player-launch.txt
sleep 2
for attempt in $(seq 1 25); do
  adb logcat -d -v brief > qa/player-logcat.txt
  if grep -E 'OrbitPlayerQA.*STATE_READY' qa/player-logcat.txt >/dev/null; then break; fi
  if grep -E 'FATAL EXCEPTION|OrbitPlayerQA.*PLAYER_ERROR' qa/player-logcat.txt >/dev/null; then
    echo "Player reported an error before reaching READY"; exit 1
  fi
  sleep 2
done
grep -E 'OrbitPlayerQA.*STATE_READY' qa/player-logcat.txt >/dev/null || {
  echo "Player did not reach READY after opening smoke video"; exit 1;
}
grep -E 'OrbitPlayerQA.*MEDIA_SESSION_READY' qa/player-logcat.txt >/dev/null || {
  echo "MediaSession was not initialized for headset media controls"; exit 1;
}
adb shell dumpsys activity activities > qa/player-activity.txt
grep -F "$PACKAGE/.PlayerActivity" qa/player-activity.txt >/dev/null || {
  echo "PlayerActivity did not remain active"; exit 1;
}

# Android's one-time immersive-mode tutorial covers the controls on a fresh emulator.
# Dismiss the system-owned overlay explicitly before judging the app's player UI.
adb shell uiautomator dump /sdcard/orbit-preflight.xml >/dev/null
adb exec-out cat /sdcard/orbit-preflight.xml > qa/preflight.xml
if grep -F 'android:id/immersive_cling_title' qa/preflight.xml >/dev/null; then
  python3 scripts/tap_android_view.py android:id/ok
  sleep 1
fi
# Controls intentionally auto-hide within seconds. UIAutomator's slow screenshot/dump
# is not reliable for asserting ephemeral toolbar visibility. Instead verify a real tap
# reaches the gesture listener, then verify the stable native player view exists.
adb logcat -c || true
adb shell input tap 410 400
sleep 1
adb logcat -d -v brief > qa/touch-logcat.txt
grep -E 'OrbitPlayerQA.*TOUCH_SINGLE_TAP' qa/touch-logcat.txt >/dev/null || {
  echo "Player did not receive single-tap gesture"; exit 1;
}
adb shell uiautomator dump /sdcard/orbit-controls.xml || true
adb exec-out cat /sdcard/orbit-controls.xml > qa/controls.xml || true
grep -F "$PACKAGE:id/playerView" qa/controls.xml >/dev/null || {
  echo "Native Media3 player view is missing"; exit 1;
}
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
sleep 3
adb shell dumpsys activity activities > qa/rotation-activity.txt
grep -F "$PACKAGE/.PlayerActivity" qa/rotation-activity.txt >/dev/null || {
  echo "PlayerActivity lost on rotation"; exit 1;
}
adb logcat -d -v brief > qa/player-logcat.txt
if grep -E 'FATAL EXCEPTION.*|OrbitPlayerQA.*PLAYER_ERROR' qa/player-logcat.txt; then
  echo "Playback smoke contains fatal/error event"; exit 1;
fi
adb shell pidof "$PACKAGE" >/dev/null
echo "PASS: API $API — Library, Settings, H264 playback READY, player controls and rotation"
