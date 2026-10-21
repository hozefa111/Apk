# Kali Ki Tidi in the existing APK

The native Home opens a bundled shared table in OnlineGameActivity. Manual/voice
entry, Settings, Search, Analytics, Share Studio and the tracker remain native.
The package stays com.hozefa.threepattipro.nativeapp; version 2.7.0 / code 21.

Canonical game/backend source is in hozefa111/hh, online/ and functions/online/.
Run its npm run sync:android after source edits. The shared-assets manifest and
Gradle preBuild check prevent stale or different rule engines from being packaged.
The generated tests/online-backend fixture is emulator-only and cannot be deployed
through its configuration. Do not edit generated copies independently.

Native identity uses the existing FirebaseAuth instance and a single-use server
exchange; administrator sign-in returns to the existing Home login controls.
This prevents a WebView-only login from becoming a different identity on resume.
The restricted native share route opens Android's invitation chooser with only a
public room code. No private card or credential is placed in an invitation URL.

Firebase rules, role provisioning, Anonymous Auth, Cloud Tasks, RTDB and runtime
signing permissions must be configured and verified using the deployment guide
in the web repository before enabling production gameplay. Provision administrator
UIDs and replace permissive rules before enabling anonymous guests. Never ship
with a guest interpreted as a tracker administrator.

Local JVM/build checks do not substitute for installed-device tests. The native
CI workflow uses full demo Firebase emulators and API 30/35; its native identity
round-trip test is enabled with kaliOnlineBackend=true. Legacy emulator scripts
now seed the synthetic tester's administrator role explicitly. Use an updated
Android System WebView for the online module.

Release readiness is tracked separately: a signed build is a candidate until
production multiplayer, Android runtime tests and real-device motion have passed.

Release verification report: the matching web branch contains
`online/VERIFICATION.md`. Final APK release remains gated on authorized backend
rollout, hosted multiplayer tests and responsive Android device instrumentation.
