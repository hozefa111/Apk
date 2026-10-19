#!/usr/bin/env bash
# Keep independent diagnostics even when one suite fails. Never turn a failure green.
set -uo pipefail
mkdir -p qa
: > qa/suite-summary.tsv
orbit_qa_status=0
run_check() {
  local orbit_check_name="$1"
  shift
  "$@" 2>&1 | tee "qa/${orbit_check_name}.log"
  local orbit_check_status=${PIPESTATUS[0]}
  printf '%s\t%s\n' "$orbit_check_name" "$orbit_check_status" >> qa/suite-summary.tsv
  if (( orbit_check_status != 0 )); then orbit_qa_status=1; fi
}
run_check native-ui bash scripts/run_emulator_smoke.sh
run_check permissions python3 scripts/run_permission_smoke.py
run_check instrumentation gradle --no-daemon :app:connectedDebugAndroidTest
run_check media-matrix python3 scripts/run_media_matrix.py qa/media
exit "$orbit_qa_status"
