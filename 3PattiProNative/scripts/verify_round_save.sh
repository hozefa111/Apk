#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_ONLINE_BACKEND=1
export KALI_ROUND_TESTS
KALI_ROUND_TESTS=$(python3 - <<'PY'
prefix='com.hozefa.threepattipro.nativeapp.'
tests=['NativeRoundSaveTest','NativeVoiceTest#confirmedVoiceRoundCreatesExactlyOneSandboxRecord','NativeVoiceTest#offlineConfirmationCannotChangeOrSaveManualDraft','OnlineGameTest#sharedTableLoadsInsideSamePackageWithSafeWebSettings','OnlineGameTest#invitationPrefillsRoomCodeInBundledTable']
import os
if os.environ.get('KALI_API')=='26': tests.append('NativeDialogTest#largeFontLandscapeKeepsHistoryAndShareActionsOnScreen')
print(','.join(prefix+t for t in tests))
PY
)
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 -Pandroid.testInstrumentationRunnerArguments.class="$KALI_ROUND_TESTS" connectedSandboxAndroidTest'
