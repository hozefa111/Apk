# 3 Patti PRO — Android app

Android WebView shell for the existing **3 Patti PRO — Score Tracker** website.

**Original, unchanged source:** https://github.com/hozefa111/hh

**Website loaded:** https://hozefa111.github.io/hh/

The Android wrapper contains no Firebase credentials, does not create a new Firebase project, and does not migrate/delete/change any Firestore data. It opens the original hosted web app, which uses its existing Firebase configuration for login, players, sessions, and rounds.

### Requirements
- Internet connection and Android 7.0+
- Same Firebase account credentials as on the website.
- Published web app must remain online.
- Some browser-only APIs (file downloads, web push) may behave differently in Android WebView.
- Google OAuth login would require additional native/browser integration; original app currently uses email/password.

### Build
Use the GitHub Actions workflow **Build 3 Patti PRO Android APK** in this branch, or Android Studio with JDK 17 + Gradle 8.9 + Android SDK 35. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

**Isolation:** Changes exist only under this folder and a separate GitHub Actions workflow on branch `three-patti-pro-android` in repository `hozefa111/Apk`. The `hozefa111/hh` repository and its Firebase backend were not modified.

**Note:** Debug-signed build is for personal device testing. It has not been tested on a physical Android phone.
