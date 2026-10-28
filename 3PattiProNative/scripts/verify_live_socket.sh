#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_ONLINE_BACKEND=1
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 -Pandroid.testInstrumentationRunnerArguments.class=com.hozefa.threepattipro.nativeapp.HybridWebViewTest connectedSandboxAndroidTest'
