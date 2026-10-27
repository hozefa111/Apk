#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_ONLINE_BACKEND=1
export KALI_DATA_TESTS
KALI_DATA_TESTS=$(python3 - <<'PY'
prefix='com.hozefa.threepattipro.nativeapp.'
tests=['NativeDataSafetyTest','NativeAnalyticsTest','NativeRoundSaveTest',
 'NativeVoiceTest#confirmedVoiceRoundCreatesExactlyOneSandboxRecord',
 'NativeVoiceTest#offlineConfirmationCannotChangeOrSaveManualDraft',
 'FirebaseGatewayEmulatorTest#manualSessionGuardBindsRoundRejectsSecondStartAndAdvancesAfterEnd',
 'FirebaseGatewayEmulatorTest#restoreCannotAddPointsBeforeDiscoveringConflictingActiveSession',
 'FirebaseGatewayEmulatorTest#roundRetryAndPayloadConflictAndVersionedDelete',
 'FirebaseGatewayEmulatorTest#sixConcurrentNativeCommandsCommitOnceAndReplayReceipts',
 'FirebaseGatewayEmulatorTest#manualSessionCascadePreservesBackupAndExactScoresAfterRepeatedDelete',
 'FirebaseGatewayEmulatorTest#onlineSessionCascadeRemovesLinkedRoundsAndKeepsOtherScores',
 'FirebaseGatewayEmulatorTest#incrementalJournalSurvivesReopenAndTracksHistoricalEditDelete',
 'FirebaseGatewayEmulatorTest#authRoleRefreshNeverPublishesTemporaryLogoutOrDuplicatesWrites',
 'HybridWebViewTest#bundledLobbyScrollAndDoubleTapRemainUsable']
print(','.join(prefix+t for t in tests))
PY
)
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 -Pandroid.testInstrumentationRunnerArguments.class="$KALI_DATA_TESTS" connectedSandboxAndroidTest'
