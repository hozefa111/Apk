#!/bin/bash
set -euo pipefail
collect() {
 result=$?
 trap - EXIT
 mkdir -p smoke-diagnostics
 if [ "$result" -ne 0 ]; then
  pid=$(adb shell pidof com.hozefa.threepattipro.nativeapp.sandbox | tr -d '\r' || true)
  if [ -n "$pid" ]; then adb shell kill -3 "$pid" || true; fi
 fi
 timeout 15s adb exec-out screencap -p > smoke-diagnostics/screen.png || true
 timeout 15s adb logcat -d > smoke-diagnostics/logcat.txt || true
 cp webview-smoke.txt smoke-diagnostics/ 2>/dev/null || true
 exit "$result"
}
trap collect EXIT
adb root
adb wait-for-device
adb install release-candidates/smoke-sandbox.apk
adb install release-candidates/smoke-test.apk
timeout --kill-after=15s 180s adb shell am instrument -w -r -e class com.hozefa.threepattipro.nativeapp.HybridWebViewTest com.hozefa.threepattipro.nativeapp.sandbox.test/androidx.test.runner.AndroidJUnitRunner > webview-smoke.txt
cat webview-smoke.txt
grep -F 'OK (1 test)' webview-smoke.txt
adb shell svc wifi disable
adb shell svc data disable
adb install previous/*.apk
adb shell am start -n com.hozefa.threepattipro.nativeapp/.MainActivity
sleep 5
adb shell mkdir -p /data/user/0/com.hozefa.threepattipro.nativeapp/files
adb shell touch /data/user/0/com.hozefa.threepattipro.nativeapp/files/upgrade-verification-marker
adb install -r release-candidates/3-Patti-PRO-v2.7.16.apk
adb shell test -f /data/user/0/com.hozefa.threepattipro.nativeapp/files/upgrade-verification-marker
adb shell am start -n com.hozefa.threepattipro.nativeapp/.MainActivity
sleep 8
adb shell pidof com.hozefa.threepattipro.nativeapp
adb shell dumpsys package com.hozefa.threepattipro.nativeapp | grep 'versionName=2.7.16'
