#!/usr/bin/env python3
"""Read-only smoke test for CURRENT native APK installed by GitHub Actions.
Tests app process and core navigation. No Firebase writes or credentials."""
import subprocess
import time
import xml.etree.ElementTree as ET
import re

PKG = "com.hozefa.threepattipro.nativeapp"
XML_FILE = "/sdcard/patti-window.xml"

def call(*cmd, timeout=90):
    return subprocess.run(cmd, text=True, capture_output=True,
                          check=True, timeout=timeout)

def alive():
    result = call("adb", "shell", "pidof", PKG, timeout=30)
    if not result.stdout.strip():
        raise AssertionError("3 Patti PRO process not running")

def hierarchy():
    for attempt in range(3):
        try:
            call("adb", "shell", "uiautomator", "dump", XML_FILE, timeout=95)
            data=call("adb", "exec-out", "cat", XML_FILE, timeout=30).stdout
            return ET.fromstring(data)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
            if attempt == 2:
                raise
            time.sleep(2)
    raise AssertionError("Unable to read Android view hierarchy")

def tap(label):
    nodes=hierarchy()
    for node in nodes.iter("node"):
        observed=node.attrib.get("text", "").strip()
        described=node.attrib.get("content-desc", "").strip()
        if observed.lower() != label.lower() and described.lower() != label.lower():
            continue
        bounds=[int(v) for v in re.findall(r"\d+", node.attrib.get("bounds",""))]
        if len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]:
            call("adb","shell","input","tap",str((bounds[0]+bounds[2])//2),
                 str((bounds[1]+bounds[3])//2),timeout=30)
            time.sleep(2)
            alive()
            print("Verified navigation tap:", label,flush=True)
            return
    raise AssertionError("Missing native navigation control: "+label)

alive()
for title in ("Ranks","Home","Players","History"):
    tap(title)

# Narrow log inspection avoids false alarms from unrelated system apps.
logs=call("adb","logcat","-d","-t","3000","-v","brief",timeout=45).stdout
if "FATAL EXCEPTION" in logs and ("Process: "+PKG) in logs:
    raise AssertionError("Android app crash detected in logcat")
print("PASS: Native APK installation, launch, and four bottom tabs",flush=True)
