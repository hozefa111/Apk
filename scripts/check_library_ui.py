#!/usr/bin/env python3
"""Check every required Orbit Pro gallery element and functional Kotlin binding."""
from pathlib import Path
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
main = ET.parse("app/src/main/res/layout/activity_main.xml").getroot()
grid = ET.parse("app/src/main/res/layout/row_video.xml").getroot()
listview = ET.parse("app/src/main/res/layout/row_video_list.xml").getroot()
def ids(root):
    return {x.attrib[A+"id"].split("/")[-1] for x in root.iter() if A+"id" in x.attrib}
required = {"continueCard","continueThumb","continueTitle","continueDetails",
    "search","clearSearch","btnSort","btnView","filterAll","filterProgress","filterRecent",
    "folderFilter","videos","count","btnGrant","navHome","navFolders","navOpen",
    "navUpdates","btnRefresh","empty","sectionTitle"}
assert required <= ids(main), sorted(required - ids(main))
cards = {"thumb","playGlyph","duration","title","details","watchProgress"}
assert cards <= ids(grid) and cards <= ids(listview), "Grid/list cards have different required fields"
activity = Path("app/src/main/java/com/orbitplayer/app/MainActivity.kt").read_text()
adapter = Path("app/src/main/java/com/orbitplayer/app/VideoAdapter.kt").read_text()
for feature in ("showSortDialog()", "showFolderDialog()", "refreshContinueCard()",
                "setGalleryMode(newValue: Boolean)", "UpdateManager.check(this)",
                "VideoRepository(applicationContext).load()"):
    assert feature in activity, f"Not functional: {feature}"
assert "GridLayoutManager(this, 2)" in activity
assert "DiffUtil.calculateDiff" in adapter
assert "row_video_list" in adapter and "row_video" in adapter
assert "playback_positions_v1" in adapter and "playback_positions_v1" in activity
assert "Cinema, reimagined." not in ET.tostring(main, encoding="unicode"), "Oversized static marketing hero persists"
assert "versionName = \"0.1.9\"" in Path("app/build.gradle.kts").read_text()
print("Orbit Pro gallery + functionality contract passed")
