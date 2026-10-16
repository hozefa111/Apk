#!/usr/bin/env python3
"""Catch required Android view layout attributes that resource compilation misses."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

namespace = "{http://schemas.android.com/apk/res/android}"
failures = []
for filename in Path("app/src/main/res").glob("layout*/*.xml"):
    for element in ET.parse(filename).getroot().iter():
        if element.tag in ("merge", "include", "requestFocus"):
            continue
        for name in ("layout_width", "layout_height"):
            if element.get(namespace + name) is None:
                failures.append(f"{filename}: {element.tag} missing {name}")
if failures:
    sys.exit("\n".join(failures))
print("Android XML dimensions valid")
