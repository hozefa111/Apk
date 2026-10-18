#!/usr/bin/env python3
"""Tap a real Android view by resource-id from an automated uiautomator dump."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

target = sys.argv[1] if len(sys.argv) > 1 else "com.orbitplayer.app:id/btnSettings"
subprocess.run(["adb","shell","uiautomator","dump","/sdcard/window.xml"],check=True)
xml = subprocess.run(["adb","exec-out","cat","/sdcard/window.xml"],
                     check=True,capture_output=True,text=True).stdout
root = ET.fromstring(xml)
found = None
for node in root.iter("node"):
    matches = node.get("content-desc") == target.removeprefix("desc:") if target.startswith("desc:") else node.get("resource-id") == target
    if matches:
        found = node.get("bounds")
        break
if not found:
    raise SystemExit("UI resource not found: " + target)
nums = [int(x) for x in re.findall(r"\d+", found)]
if len(nums) != 4:
    raise SystemExit("Unparseable Android view bounds: " + found)
x, y = (nums[0]+nums[2])//2, (nums[1]+nums[3])//2
print("Tapping",target,"at",x,y)
subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
