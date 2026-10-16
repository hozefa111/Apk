# 3 Patti PRO Native Android v1.3

This project is a genuine Android application, not a WebView. It uses the same Firebase
project and Firestore collection schemas as the original game at https://github.com/hozefa111/hh .

## Web application as source of truth
- Original 3 Patti Hukum/Partner/Non-partner scoring rule is tested in unit tests.
- Dashboard now uses active players, number of game rounds, top winning player and the sum of positive player balances ("On Table"), following the web edition.
- Leaderboard date filters, player profile stats, head-to-head, history and round breakdown remain native controls.
- Sharing uses specialized native PNG composition matching web share-card sections: centered gold brand, dark cards, role icons, win/lose badges, positive/negative scores, medals and session awards.
- Saving a round waits for a successful Firestore write before resetting the form or offering the Share Image dialogue.
- Existing Firebase user records and HH repository are not modified as part of building the APK.

## Test status and known differences
The GitHub workflow runs offline Java scoring tests and compiles/debug-signs the APK, checking that HTML, JS and CSS are absent. Compilation is not a substitute for tapping every button on a physical Android device. Real-time listeners in the web app are approximated by 12-second Firestore REST polling; native Android feature parity is therefore not exact. Browser-only localStorage migration, Firebase Messaging push, and the web's destructive clear-all action are intentionally not in this build. Color/spacing/fonts approximate the web site but are not pixel-identical due to native Android rendering.

## Data safety
Do not alter, delete or force-push the HH repository. All changes stay in the separate `three-patti-pro-native` branch of `hozefa111/Apk`. APK build actions read only local source and do not write to Firebase.
