#!/usr/bin/env bash
set -euo pipefail
verification_project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
verification_images="$verification_project/app/build/verification-screenshots"
mkdir -p "$verification_images"
pull_screenshots() {
    verification_status=$?
    trap - EXIT
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
firebase emulators:exec --project demo-three-patti --only auth,firestore \
  --config tests/emulator/firebase.json \
  'python3 scripts/seed_emulator.py && gradle -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedSandboxAndroidTest'
