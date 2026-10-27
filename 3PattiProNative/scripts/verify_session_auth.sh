#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_ONLINE_BACKEND=1
export KALI_TEST_CLASSES
KALI_TEST_CLASSES=$(python3 - <<'PY'
names=['authRoleRefreshNeverPublishesTemporaryLogoutOrDuplicatesWrites','pendingAuthProofIsRevalidatedBeforeWritingAndSurvivesReopen','revokedAdminAndExpiredAuthenticationNeverInvokeMutation','authNetworkFailureIsNotMisreportedAsLogoutAndSignoutIsBlocked','manualSessionCascadePreservesBackupAndExactScoresAfterRepeatedDelete','onlineSessionCascadeRemovesLinkedRoundsAndKeepsOtherScores','sixConcurrentNativeCommandsCommitOnceAndReplayReceipts','externalEditArrivesDirectlyAndOldDialogCannotOverwriteIt']
print(','.join('com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#'+n for n in names))
PY
)
adb shell svc power stayon true
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.class="$KALI_TEST_CLASSES" connectedSandboxAndroidTest'
