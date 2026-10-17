#!/usr/bin/env bash
set -euo pipefail
verification_project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
verification_images="$verification_project/app/build/verification-screenshots"
mkdir -p "$verification_images"
pull_screenshots() {
    verification_status=$?
    trap - EXIT
    if ! adb pull /sdcard/Android/data/com.hozefa.threepattipro.nativeapp.sandbox/files/verification/. "$verification_images/"; then
        if [ "$verification_status" -eq 0 ]; then verification_status=1; fi
    fi
    exit "$verification_status"
}
trap pull_screenshots EXIT
cd "$verification_project"
firebase emulators:exec --project demo-three-patti --only auth,firestore \
  --config tests/emulator/firebase.json \
  'python3 scripts/seed_emulator.py && gradle -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedSandboxAndroidTest'
