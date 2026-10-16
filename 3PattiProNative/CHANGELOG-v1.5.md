# 3 Patti PRO Native v1.5 – Audited fixes

This is a native Android app (no WebView). The original hozefa111/hh repository and Firebase records were not modified.

Fixes:
- Main UI no longer disables every normal tap while an admin write runs; the database gateway prevents concurrent duplicate writes.
- A stored Firebase refresh token no longer grants an unverified admin session; it waits for token verification.
- Player list now uses compact tappable rows and a contextual action menu instead of three full-width buttons per player.
- Round history now uses compact cards and a contextual Details / Share / Delete menu instead of heavy repeated buttons.
- Session history uses compact cards with contextual summary, edit, share and delete actions.
- Head-to-head W/L counts only matches played against opponents, matching the original web implementation.
- Session-end stores the real current winning streaks.
- Removed duplicate Android back-navigation branch, corrected the bid-unit text and simplified unnecessary labels.
- All editing/deleting still requires explicit user action and confirmations.

Verification: automated Java score-rule unit tests and Android APK build via GitHub Actions. A successful build is not the same as end-to-end device or live Firebase authentication testing; those are still unverified.
