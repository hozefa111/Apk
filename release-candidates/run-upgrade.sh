#!/bin/bash
set -euo pipefail
apk_package=com.hozefa.threepattipro.nativeapp
adb root
adb wait-for-device
adb shell svc wifi disable
adb shell svc data disable
adb install previous/*.apk
adb shell am start -n "$apk_package/.MainActivity"
sleep 5
adb shell am force-stop "$apk_package"
python3 release-candidates/seed-old-cache.py
adb push compat-cache.db /data/local/tmp/compat-cache.db
adb shell mkdir -p "/data/user/0/$apk_package/databases"
adb shell rm -f "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db-wal" "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db-shm" "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db-journal"
adb shell cp /data/local/tmp/compat-cache.db "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db"
cache_uid=$(adb shell stat -c %u "/data/user/0/$apk_package" | tr -d '\r')
[[ "$cache_uid" =~ ^[0-9]+$ ]]
adb shell chown "$cache_uid:$cache_uid" "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db"
adb shell chmod 600 "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db"
adb shell mkdir -p "/data/user/0/$apk_package/files"
adb shell touch "/data/user/0/$apk_package/files/upgrade-verification-marker"
mkdir -p upgrade-evidence
adb exec-out cat "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db" > upgrade-evidence/cache-before.db
adb install -r release-candidates/3-Patti-PRO-v2.7.23.apk
adb exec-out cat "/data/user/0/$apk_package/databases/3patti_tracker_cache_v1.db" > upgrade-evidence/cache-after-install.db
cmp upgrade-evidence/cache-before.db upgrade-evidence/cache-after-install.db
adb shell test -f "/data/user/0/$apk_package/files/upgrade-verification-marker"
adb shell am start -n "$apk_package/.MainActivity"
sleep 8
adb shell pidof "$apk_package"
adb shell dumpsys package "$apk_package" | grep 'versionName=2.7.23'
mkdir -p upgrade-evidence
for page in 0 1 2 3 4 5; do
 timeout 20s adb shell uiautomator dump /sdcard/release-ui.xml
 adb pull /sdcard/release-ui.xml "upgrade-evidence/ui-$page.xml"
 ! grep -q 'Load complete history' "upgrade-evidence/ui-$page.xml"
 if grep -q 'Upgrade cache player' "upgrade-evidence/ui-$page.xml"; then break; fi
 adb shell input swipe 160 550 160 180 350
 sleep 1
done
grep -l 'Upgrade cache player' upgrade-evidence/ui-*.xml
adb exec-out screencap -p > upgrade-evidence/cache-visible.png
echo 'Stable certificate update, retained marker/cache, visible saved player and no manual Load button: PASS'

# Exercise the signed release's native session controls through actual touches.
function tap_label() {
 for step in 0 1 2 3 4; do
  adb shell uiautomator dump /sdcard/release-ui.xml >/dev/null
  adb pull /sdcard/release-ui.xml upgrade-evidence/current.xml >/dev/null
  if position=$(python3 release-candidates/tap-ui.py "$1"); then
   adb shell input tap $position
   sleep 2
   return 0
  fi
  adb shell input swipe 180 550 180 250 400
  sleep 1
 done
 echo "Cannot reach $1" >&2
 return 1
}
tap_label Ranks
tap_label Sessions
tap_label 'Select Session'
tap_label 'Upgrade session'
adb shell uiautomator dump /sdcard/release-ui.xml >/dev/null
adb pull /sdcard/release-ui.xml upgrade-evidence/session-selected.xml >/dev/null
grep -q 'Upgrade session' upgrade-evidence/session-selected.xml
for page in 0 1 2 3 4; do
 if grep -q '+310' upgrade-evidence/session-selected.xml; then break; fi
 adb shell input swipe 180 550 180 250 400
 sleep 1
 adb shell uiautomator dump /sdcard/release-ui.xml >/dev/null
 adb pull /sdcard/release-ui.xml upgrade-evidence/session-selected.xml >/dev/null
done
grep -q '+310' upgrade-evidence/session-selected.xml
! grep -q 'text="Today"' upgrade-evidence/session-selected.xml
tap_label 'Big Scoreboard'
adb shell uiautomator dump /sdcard/release-ui.xml >/dev/null
adb pull /sdcard/release-ui.xml upgrade-evidence/big-session.xml >/dev/null
grep -q 'Upgrade session' upgrade-evidence/big-session.xml
grep -q 'Completed session scores' upgrade-evidence/big-session.xml
adb exec-out screencap -p > upgrade-evidence/session-big-signed-release.png
echo 'Signed APK: session selection, isolated score, Big Scoreboard selection retention: PASS'
