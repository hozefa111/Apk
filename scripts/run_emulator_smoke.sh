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

adb shell mkdir -p /sdcard/Movies
adb push smoke.mp4 /sdcard/Movies/orbit_smoke.mp4
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
adb shell input keyevent 4
sleep 1

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
# Check playback UI remains reachable after tapping the video surface.
adb shell input tap 410 400
sleep 1
adb shell uiautomator dump /sdcard/orbit-controls.xml || true
adb exec-out cat /sdcard/orbit-controls.xml > qa/controls.xml || true
if ! grep -F "$PACKAGE:id/btnPlayPause" qa/controls.xml >/dev/null; then
  adb shell input tap 410 400
  sleep 1
  adb shell uiautomator dump /sdcard/orbit-controls.xml || true
  adb exec-out cat /sdcard/orbit-controls.xml > qa/controls.xml || true
fi
grep -F "$PACKAGE:id/btnPlayPause" qa/controls.xml >/dev/null || {
  echo "Player play/pause control is not reachable"; exit 1;
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
