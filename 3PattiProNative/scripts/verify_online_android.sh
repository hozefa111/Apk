#!/usr/bin/env bash
set -euo pipefail
KALI_PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
export KALI_PROJECT
export KALI_ONLINE_BACKEND=1
cd "$KALI_PROJECT"
# A UI suite may wait for emulator network work longer than the device's idle
# screen timeout. Keep the *test device* awake; never change production app flags.
mkdir -p app/build/verification
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 2147483647
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# Capture evidence while the emulator is alive, including failures that would
# otherwise leave an empty post-exit logcat after the VM disappears.
adb logcat -v threadtime > app/build/verification/continuous-device.log 2>&1 &
KALI_LOGCAT_PID=$!
(
    while true; do
        date -u
        ps -eo pid,rss,comm --sort=-rss | head -n 12
        timeout 5s adb shell dumpsys power | sed -n '/mWakefulness=/p;/mIsPowered=/p;/mStayOn=/p'
        timeout 5s adb shell dumpsys window | sed -n '/mCurrentFocus=/p;/mFocusedApp=/p;/mDreamingLockscreen=/p'
        sleep 30
    done
) > app/build/verification/device-host-status.log 2>&1 &
KALI_MONITOR_PID=$!
collect_online_diagnostics() {
    kill "$KALI_MONITOR_PID" "$KALI_LOGCAT_PID" 2>/dev/null || true
    mkdir -p app/build/verification/screens
    timeout 15s adb shell dumpsys window > app/build/verification/final-window.txt || true
    timeout 15s adb shell dumpsys power > app/build/verification/final-power.txt || true
    timeout 15s adb exec-out screencap -p > app/build/verification/final-screen.png || true
    timeout 15s adb logcat -d -s AndroidRuntime TestRunner chromium > app/build/verification/device.log || true
    local screen_names
    screen_names="$(timeout 15s adb shell run-as com.hozefa.threepattipro.nativeapp.sandbox ls files/verification 2>/dev/null | tr -d '\r' || true)"
    for screen in $screen_names; do
        if [[ "$screen" =~ ^[a-zA-Z0-9_.-]+\.png$ ]]; then
            timeout 15s adb exec-out run-as com.hozefa.threepattipro.nativeapp.sandbox cat "files/verification/$screen" > "app/build/verification/screens/$screen" || true
        fi
    done
}
trap collect_online_diagnostics EXIT
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions \
    --config tests/online-backend/firebase.json \
    'python3 "$KALI_PROJECT/scripts/seed_emulator.py" && gradle -p "$KALI_PROJECT" --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" testSandboxUnitTest -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=180000 connectedSandboxAndroidTest'
