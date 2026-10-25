Fixes the session selector flickering while live scores refresh. The dropdown now stays open, retains unchanged rows and keeps its scroll position. Session name/status/list changes update in place. No game, scoring, backend, account or Firebase data changes.

Verified: local Android sandbox/test and signed release builds; Android 11 emulator regression with 20 rapid score updates asserting the same dialog window, retained rows, retained scroll and in-place session rename. Release publication additionally requires installing over v2.7.18 without clearing data, preserving cache and exercising session selection and Big Scoreboard through actual touches. All 15 bundled game asset hashes and stable signing certificate verified. No physical-phone testing was performed.

Version 2.7.19 (code 40). Same package and signing identity. Source: three-patti-pro-native @ c95a640.
