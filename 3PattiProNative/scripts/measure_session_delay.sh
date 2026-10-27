#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export SESSION_BASELINE=false
firebase emulators:exec --project demo-three-patti --only auth,firestore --config tests/emulator/firebase.json \
 'python3 scripts/seed_emulator.py && gradle -Pandroid.testInstrumentationRunnerArguments.sessionBaseline=$SESSION_BASELINE -Pandroid.testInstrumentationRunnerArguments.class="$KALI_TEST_CLASSES" connectedSandboxAndroidTest'
