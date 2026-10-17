# 3 Patti PRO Native Android — 2.2 development

Native Java Android application; no WebView. Source of truth: `hozefa111/hh`
commit `8b2189d28d7bd9d9c21f509e87bf89717816898f`.

This is development source, not a certified final release. See
[verification and remaining gates](VERIFICATION.md).

## Implemented in this iteration

- Firebase Auth SDK persistence; snapshot listeners trigger a debounced full REST
  reconciliation, plus a 60-second reconciliation watchdog. Private cached data is
  read-only offline; app mutations use REST and are never queued offline.
- Server timestamps; optimistic document version checks; persisted round identity,
  locked ambiguous-save draft, payload verification on retries, recovery backups
  before deletions and restoration.
- Month/year and DST-safe inclusive date filters; HH positive-score hot-streak
  badge; stable player creation ordering for tied ranks; complete history scores,
  opponents, session duration and timer.
- Settings, three appearance palettes, compact history, haptics, session wake option,
  cross-table search, player performance and cumulative score charts.
- Share Studio with three palettes, single-page-at-a-time preview, paginated cards,
  wrapped award values, gallery save on Android 10+, folder export on Android 8/9,
  and multiple-image sharing.
- Typed Firestore JSON backup/export, restore missing records without overwriting
  existing data, explicit HH `3PattiProState` JSON import into an empty player
  collection. Clear history requires a saved current backup plus two confirmations,
  including typed `CONFIRM`; players are retained as in HH.

## Build and test

Use JDK 17, Gradle 8.9 and Android SDK 35:

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleSandbox assembleSandboxAndroidTest
firebase emulators:exec --project demo-three-patti --only auth,firestore \
  --config tests/emulator/firebase.json \
  "python3 scripts/seed_emulator.py && gradle connectedSandboxAndroidTest"
```

The `sandbox` application has a separate ID ending `.sandbox` and **compiled-in**
`demo-three-patti`/local emulator endpoints. Production endpoints cannot be selected
by an Intent. Instrumentation write tests refuse to execute against other variants.
Fixtures are synthetic. Unit scoring vectors execute HH's actual scoring function;
regenerate with `node scripts/generate_parity_vectors.js /path/to/hh/app.js`.

## Data safety and limits

Development/build/tests do not modify HH or production Firestore. Production audit
uses GET only. UI mutations require a user action and a verified login/live state.
Atomic rename, restore, migration and clear are limited to 500 documents. Larger
operations fail before changing any record. The read path refuses histories over
10,000 records rather than publishing partial data. This safety limit means full
unbounded web parity is not claimed.

Recovery archives are app-private and are lost on uninstall. Export them through
Settings for durable storage. Backups contain names and scores; no auth tokens.
Legacy REST sessions require a one-time sign-in after upgrading to SDK auth.

## Final signing

Configure `THREE_PATTI_STORE_FILE`, `THREE_PATTI_STORE_PASSWORD`,
`THREE_PATTI_KEY_ALIAS`, `THREE_PATTI_KEY_PASSWORD` outside source control.
Run `gradle verifyReleaseSigning` before a release. The keystore must match the
installed APK for update-in-place. Debug APKs are verification builds and are not
uploaded as final releases by the native build workflow.
