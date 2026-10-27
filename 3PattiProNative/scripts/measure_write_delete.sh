#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_ONLINE_BACKEND=1
export KALI_WRITE_BASELINE=false
export KALI_WRITE_TESTS=com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#roundRetryAndPayloadConflictAndVersionedDelete,com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#manualSessionCascadePreservesBackupAndExactScoresAfterRepeatedDelete,com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#onlineSessionCascadeRemovesLinkedRoundsAndKeepsOtherScores,com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#measuredWriteDeletePipelinePreservesExactData,com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#unchangedVerifiedJournalSkipsRewriteButCorruptCacheStillBootstraps,com.hozefa.threepattipro.nativeapp.FirebaseGatewayEmulatorTest#pendingAuthProofIsRevalidatedBeforeWritingAndSurvivesReopen
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.kaliOnlineBackend=true -Pandroid.testInstrumentationRunnerArguments.writeBaseline=$KALI_WRITE_BASELINE -Pandroid.testInstrumentationRunnerArguments.class="$KALI_WRITE_TESTS" connectedSandboxAndroidTest'
