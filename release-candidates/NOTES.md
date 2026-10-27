The confirmed-round save flow from PR #33 is included and retained without rewriting it. After a manual or voice round save, the app stays on Round, keeps the selected players, resets bid to 155, and clears Hukum, partners and Win/Lose. The existing saved-round confirmation remains available.

Failed or unconfirmed saves retain the draft and its original round ID for retry. No offline-saving queue, Firebase rules, incremental-sync, scoring, session/history or online-game changes are introduced. Version 2.7.26 (47), following the actual latest release 2.7.25 (46).

Focused tests cover manual next-round entry, player retention, field reset, voice save, failed-save/reopen retry and an already-committed record with lost confirmation (no duplicate). Existing WebView timeout cases are checked separately using the full local Auth/Firestore/Functions fixture. All synthetic data stays in emulators.

Same package and official signing identity. Physical-phone testing has not been performed.

Focused verification: 20 checks passed in the original Android 8/11/15 run. Its two Android 8 WebView cases failed in the test harness (global instrumentation idle waits and raw optional chaining unsupported by WebView 69). The app bundle already targets Chrome 67; its runtime was unchanged. Both corrected test cases passed in the focused rerun. The earlier large-font Android 8 case also passed. The full historical suite was not rerun.

Evidence: [Round/voice tests](https://github.com/hozefa111/Apk/actions/runs/40500883711), [corrected WebView checks](https://github.com/hozefa111/Apk/actions/runs/40503129609). All 18 bundled game assets match v2.7.25.
