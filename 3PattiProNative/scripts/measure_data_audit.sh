#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
firebase emulators:exec --project demo-three-patti --only auth,firestore --config tests/emulator/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.class=com.hozefa.threepattipro.nativeapp.PerformanceAuditTest connectedSandboxAndroidTest'
