#!/usr/bin/env bash
set -euo pipefail
KALI_PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
export KALI_PROJECT
export KALI_ONLINE_BACKEND=1
cd "$KALI_PROJECT"
collect_online_diagnostics() {
    mkdir -p app/build/verification/screens
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
