#!/usr/bin/env python3
"""Emulator UI contract: transport controls must remain below the video midpoint."""
import re, subprocess, xml.etree.ElementTree as ET

subprocess.run(["adb","shell","uiautomator","dump","/sdcard/controls.xml"], check=True)
xml = subprocess.check_output(["adb","exec-out","cat","/sdcard/controls.xml"],text=True)
root=ET.fromstring(xml)
ids={}
for node in root.iter("node"):
    name=node.get("resource-id","").rsplit("/",1)[-1]
    bounds=[int(v) for v in re.findall(r"\d+",node.get("bounds",""))]
    if len(bounds)==4 and name:
        ids[name]=bounds

required={"btnPlayPause","btnNext","btnPrevious","playerSeekBar","playerView"}
missing=required-set(ids)
assert not missing, "Missing video controls: "+", ".join(sorted(missing))
container=ids["playerView"]
total_height=container[3]-container[1]
for name in ("btnPlayPause","btnNext","btnPrevious"):
    view=ids[name]
    center=(view[1]+view[3])/2
    assert center >= container[1]+total_height*0.52, f"{name} is obstructing the middle of the movie"
assert ids["btnPlayPause"][2]-ids["btnPlayPause"][0]>=48,"Play touch target is too small"
print("Orbit Pro cinematic bottom controls: PASS")
