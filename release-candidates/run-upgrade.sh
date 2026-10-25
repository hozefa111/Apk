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
adb install -r release-candidates/3-Patti-PRO-v2.7.17.apk
adb shell test -f "/data/user/0/$apk_package/files/upgrade-verification-marker"
adb shell am start -n "$apk_package/.MainActivity"
sleep 8
adb shell pidof "$apk_package"
adb shell dumpsys package "$apk_package" | grep 'versionName=2.7.17'
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
