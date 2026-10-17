#!/usr/bin/env bash
set -euo pipefail
verification_project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
verification_images="$verification_project/app/build/verification-screenshots"
mkdir -p "$verification_images"
pull_screenshots() {
    adb pull /sdcard/Android/data/com.hozefa.threepattipro.nativeapp.sandbox/files/verification/. "$verification_images/" || true
}
trap pull_screenshots EXIT
cd "$verification_project"
firebase emulators:exec --project demo-three-patti --only auth,firestore \
  --config tests/emulator/firebase.json \
  'python3 scripts/seed_emulator.py && gradle connectedSandboxAndroidTest'
