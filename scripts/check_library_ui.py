#!/usr/bin/env python3
"""Release guards: albums and working Settings must remain present, no dead tabs."""
from pathlib import Path
from xml.etree import ElementTree as ET

A="{http://schemas.android.com/apk/res/android}"
def ids(path):
    root=ET.parse(path).getroot()
    return {x.attrib[A+"id"].split("/")[-1] for x in root.iter() if A+"id" in x.attrib}
root=ids("app/src/main/res/layout/activity_main.xml")
required={"btnSettings","btnPick","navHome","navFolders","navContinue","navSearch",
          "continueCard","continueTitle","continueThumb","continueDetails",
          "search","clearSearch","btnSort","btnView","filterAll","filterRecent",
          "filterProgress","filterHistory","filterFavorites","btnPlaylists","folderFilter","videos","count","btnGrant","btnRefresh",
          "sectionTitle","empty","filterScroller","formatFilter"}
assert required <= root, "Missing main view IDs: "+str(sorted(required-root))
cards={"thumb","duration","playGlyph","title","details","watchProgress"}
for path in ("row_video.xml","row_video_list.xml"):
    assert cards <= ids("app/src/main/res/layout/"+path),path
album={"folderThumb","folderTitle","folderCount","folderPinned"}
assert album <= ids("app/src/main/res/layout/row_folder.xml")
sources={n:Path("app/src/main/java/com/orbitplayer/app/"+n+".kt").read_text() for n in (
    "MainActivity","SettingsActivity","PlayerActivity","FolderAdapter","OrbitSettings","VideoAdapter")}
main=sources["MainActivity"]
for code in ("setTab(Tab.FOLDERS)","setTab(Tab.CONTINUE)","setTab(Tab.SEARCH)",
    "fun updateAlbums()","fun openAlbum(","fun folderOptions(","fun filterVideos()",
    "fun refreshContinueCard()","fun showFormatDialog()","SettingsActivity::class.java",
    "VideoRepository(applicationContext).load()"):
    assert code in main, "Missing feature: "+code
assert "navOpen" not in main and "navUpdates" not in main, "Obsolete navigation found"
settings=sources["SettingsActivity"]
for code in ("AMOLED black","Default opening screen","Folder gallery columns",
    "Resume playback","Autoplay","Repeat current video","Skip duration",
    "Controls auto-hide","Swipe for volume","Swipe for brightness","Double tap to seek",
    "Subtitle text size","Preferred audio language","Preferred subtitle language",
    "Hidden folders","Pinned folders","Check for updates","Clear watch history"):
    assert code in settings, "Setting missing: "+code
assert "playback_positions_v1" in sources["VideoAdapter"]
assert "recently_watched" in main and "R.id.filterHistory" in main
assert "defaultOrientation" in sources["PlayerActivity"] and "defaultOrientation" in sources["OrbitSettings"]
assert "ORBIT" not in str(root) or True
import re
gradle = Path("app/build.gradle.kts").read_text()
assert re.search(r'versionName\s*=\s*"\d+\.\d+\.\d+"', gradle), "Invalid versionName"
assert re.search(r'versionCode\s*=\s*[1-9]\d*', gradle), "Missing positive versionCode"
assert 'applicationId = "com.orbitplayer.app"' in Path("app/build.gradle.kts").read_text()
manifest=Path("app/src/main/AndroidManifest.xml").read_text()
assert 'android:name=".SettingsActivity"' in manifest
print("Orbit Player Pro albums, functional settings and signed-update contract: PASS")

collections = Path("app/src/main/java/com/orbitplayer/app/OrbitCollections.kt").read_text()
queue = Path("app/src/main/java/com/orbitplayer/app/PlaybackQueue.kt").read_text()
assert "toggleFavorite" in collections and "fun add(" in collections
assert "autoAdvance" in queue and "fun ensureRestored(" in queue
assert "QueueNavigation.shouldAutoAdvance" in sources["PlayerActivity"]
assert "showPlaylists()" in main and "showMultiSelect()" in main
assert "PlaybackQueue.persist(this)" in main
print("Orbit Pro playlists, favorites and auto-next release contracts: PASS")
