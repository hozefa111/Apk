# Orbit Player Pro — playback UX audit (development v0.3.1)

## Core principle
The video itself is the hero. Do not draw any persistent controls over its centre, whether playing or paused. All seek/transport controls live in one bottom fade. Only temporary gesture feedback may briefly appear at the top.

## Element decisions
| Element | Placement | Reason |
|---|---|---|
| Title / Back | Top-left fade | Necessary context and one-handed exit; fades away during viewing |
| Picture-in-picture / More | Top-right fade | Rare but important; kept away from primary transport |
| Play, pause, 10-second seek, next/previous | Bottom row below timeline | Never covers the film's centre; thumb-friendly spacing |
| Timeline + current / remaining time | First bottom row | Scrubbing together with duration feedback; buffered portion visible |
| Lock, subtitles, audio, speed, fit | Bottom shortcut row in portrait | The five most useful viewing adjustments, no top overcrowding |
| Rotate, video info, sleep timer, repeat, advanced actions | Dark rounded bottom sheet | Infrequent settings do not permanently consume screen |
| Landscape | Shorter top/bottom bars, quick shortcuts hidden | Preserve video height; advanced actions remain accessible in More |
| Gestures | Background transparent touch layer | Double-tap skip, horizontal seek, swipe volume/brightness, pinch fit and long-press 2x |
| Gesture HUD | Small pill near top | Provides feedback only during gesture; does not obscure movie |
| Playhead and buffers | Cyan/purple seekbar | Clear contrast and visible progress on bright/dark videos |

## Implemented behavior (device verification tracked separately)
- Auto-hide follows the saved controls timeout; tapping video toggles both bars. Touch exploration keeps controls accessible.
- Bottom transport and its timeline share one parent, so no independent overlay remains visible.
- Locked state hides controls; unlock pill returns on tap.
- PiP hides touch surface and playback chrome.
- State and resume positions remain in the previous preference file, and package name is unchanged.
- Signed build uses the existing private keystore; increasing versionCode allows in-place updates.

## Checks prior to release
- XML attributes and required view IDs
- Kotlin debug and optimized release compilation and unit tests
- GitHub Actions signed release, keystore identity and APK checksum
- Android emulator smoke test, including portrait screenshot where available
- Actual-device playback and gesture smoothness should still be confirmed on the phone, because CI alone cannot prove all hardware codecs or devices.

Current library/persistence/performance repairs, automated evidence, and unresolved device/web checks are tracked in [the October audit](ORBIT_RELEASE_AUDIT_2026_10_18.md). The original web design is unverified; premium visual parity is not established by passing XML checks.
