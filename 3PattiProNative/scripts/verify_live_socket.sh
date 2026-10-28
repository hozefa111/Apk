#!/usr/bin/env bash
set -euo pipefail
export KALI_TEST_CLASS=com.hozefa.threepattipro.nativeapp.HybridWebViewTest
# Reuse the established awake/keyguard/failure-diagnostic harness. Android 15
# rejects targeted pointer injection when the launcher/dream owns the window.
exec bash "$(dirname "$0")/verify_online_android.sh"
