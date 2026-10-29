#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KALI_TEST_CLASSES='com.hozefa.threepattipro.nativeapp.ScoreChartUiTest,com.hozefa.threepattipro.nativeapp.SessionDelayTest#signedOutRanksAndBigScoreboardUpdateWithoutAuthOrGamePresence'
adb shell svc power stayon true
firebase emulators:exec --project demo-three-patti --only auth,firestore,database,functions --config tests/online-backend/firebase.json \
 'python3 scripts/seed_emulator.py && gradle --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pandroid.testInstrumentationRunnerArguments.class="$KALI_TEST_CLASSES" connectedSandboxAndroidTest'
mkdir -p app/build/verification
for name in score-chart-lifetime score-chart-session; do
 adb pull "/data/local/tmp/$name.png" "app/build/verification/$name.png"
done
python3 - <<'PYVERIFY'
from pathlib import Path
for p in Path('app/build/verification').glob('score-chart-*.png'):
 assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), 'Invalid screenshot '+str(p)
PYVERIFY
