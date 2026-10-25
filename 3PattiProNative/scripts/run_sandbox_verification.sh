#!/usr/bin/env bash
set -euo pipefail
verification_project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
verification_images="$verification_project/app/build/verification-screenshots"
mkdir -p "$verification_images"
pull_screenshots() {
    verification_status=$?
    trap - EXIT
    if [ -n "${verification_probe_pid:-}" ]; then kill "$verification_probe_pid" 2>/dev/null || true; fi
    # Always retain the final screen and test errors, including stalled UI runs.
    timeout 15s adb exec-out screencap -p > "$verification_images/diagnostic-screen.png" || true
    timeout 15s adb logcat -d -s AndroidRuntime TestRunner System.err > "$verification_images/diagnostic-test-log.txt" || true
    if verification_files=$(adb shell run-as com.hozefa.threepattipro.nativeapp.sandbox ls files/verification); then
        verification_files=${verification_files//$'\r'/}
        for verification_name in $verification_files; do
            case "$verification_name" in
                *[!a-z0-9.-]*|*.png.png|.*) verification_status=1; continue ;;
                *.png) ;;
                *) verification_status=1; continue ;;
            esac
            if ! adb exec-out run-as com.hozefa.threepattipro.nativeapp.sandbox cat "files/verification/$verification_name" > "$verification_images/$verification_name"; then
                verification_status=1
            fi
            if [ ! -s "$verification_images/$verification_name" ]; then verification_status=1; fi
        done
        if [ -z "$verification_files" ]; then verification_status=1; fi
    else
        verification_status=1
    fi
    exit "$verification_status"
}
trap pull_screenshots EXIT
cd "$verification_project"
# Preserve a few read-only live diagnostics so slow/stalled UI tests can be
# inspected before the complete instrumentation runner has exited.
if [ -n "${RUNNER_WORKSPACE:-}" ]; then
(
    verification_upload_script="$(dirname "$RUNNER_WORKSPACE")/_actions/actions/upload-artifact/v4/dist/upload/index.js"
    for verification_probe in 1 2 3; do
        sleep 120
        verification_live="$verification_project/app/build/live-diagnostic-$verification_probe"
        mkdir -p "$verification_live"
        timeout 15s adb exec-out screencap -p > "$verification_live/screen.png" || true
        timeout 15s adb logcat -d -s AndroidRuntime TestRunner System.err > "$verification_live/test-log.txt" || true
        verification_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
        if [ -f "$verification_upload_script" ]; then
            env INPUT_NAME="native-live-check-$verification_api-$verification_probe" INPUT_PATH="$verification_live" 'INPUT_IF-NO-FILES-FOUND=warn' 'INPUT_RETENTION-DAYS=1' 'INPUT_COMPRESSION-LEVEL=6' INPUT_OVERWRITE=false 'INPUT_INCLUDE-HIDDEN-FILES=false' node "$verification_upload_script" || true
        fi
    done
) &
verification_probe_pid=$!
fi
timeout --signal=INT --kill-after=30s 15m firebase emulators:exec --project demo-three-patti --only auth,firestore \
  --config tests/emulator/firebase.json \
  'python3 scripts/seed_emulator.py && gradle -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=180000 ${KALI_TEST_CLASSES:+-Pandroid.testInstrumentationRunnerArguments.class=$KALI_TEST_CLASSES} connectedSandboxAndroidTest'
