#!/usr/bin/env python3
"""Guard the Orbit Player Pro layout against intrusive or unusable playback controls."""
from pathlib import Path
from xml.etree import ElementTree as ET
import sys

ANDROID = "{http://schemas.android.com/apk/res/android}"
source = Path("app/src/main/res/layout/activity_player.xml")
root = ET.parse(source).getroot()
elements = {view.attrib.get(ANDROID+"id", "").split("/")[-1]:view for view in root.iter()
            if view.attrib.get(ANDROID+"id")}
checks = []
def check(ok, detail):
    if not ok:
        checks.append(detail)

check("centerControls" in elements, "Transport controls not found")
check("bottomControls" in elements, "Bottom controls not found")
check("quickActions" in elements, "Bottom quick actions missing")
check("topBar" in elements, "Top bar missing")
check("gestureLayer" in elements, "Touch surface missing")

def children_of(parent_id):
    parent = elements.get(parent_id)
    if parent is None:
        return set()
    return {child.attrib.get(ANDROID+"id", "").split("/")[-1] for child in parent.iter()}

bottom = children_of("bottomControls")
top = children_of("topBar")
for item in ["btnPrevious","btnRewind","btnPlayPause","btnForward","btnNext",
             "playerSeekBar","timeCurrent","timeDuration","btnLock","btnSubs",
             "btnTracks","btnSpeed","btnAspect"]:
    check(item in bottom, f"{item} must be in the bottom dock")
for item in ["btnPip","btnMore","playerTitle","back"]:
    check(item in top, f"{item} must be in the top bar")

for child in root:
    item_id = child.attrib.get(ANDROID+"id", "").split("/")[-1]
    check(item_id != "centerControls", "Transport is still a center-screen overlay!")

check("btnRotate" not in top, "Rotate should be in advanced options")
check("btnTracks" not in top, "Track chooser clutters the top bar")
check("btnInfo" not in top, "Video info clutters the top bar")

player_activity = Path("app/src/main/java/com/orbitplayer/app/PlayerActivity.kt").read_text()
check("fun adjustForScreenSize()" in player_activity, "Landscape compact controls missing")
check("fun showMoreOptions()" in player_activity and "R.layout.orbit_pro_sheet" in player_activity,
      "Premium advanced settings sheet missing")
check("listOf(topBar, bottomControls)" in player_activity,
      "Top and bottom must hide/show together")
manifest = Path("app/src/main/AndroidManifest.xml").read_text()
gradle = Path("app/build.gradle.kts").read_text()
check('android:label="Orbit Player Pro"' in manifest, "App label must say Orbit Player Pro")
check('applicationId = "com.orbitplayer.app"' in gradle, "Package ID changed: breaks updates")
check('versionName = "0.1.8"' in gradle, "Wrong release version")

if checks:
    print("\n".join(checks), file=sys.stderr)
    sys.exit(1)
print("Orbit Pro playback UX layout contract: PASS")
