#!/usr/bin/env python3
"""Check denied/partial access UI without treating permission loss as deleted files."""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

package='com.orbitplayer.app'
qa=Path('qa/permissions');qa.mkdir(parents=True,exist_ok=True)
def adb(*args,check=True):
    return subprocess.run(['adb',*args],capture_output=True,text=True,check=check,timeout=120).stdout
api=int(adb('shell','getprop','ro.build.version.sdk').strip())
permission='android.permission.READ_MEDIA_VIDEO' if api>=33 else 'android.permission.READ_EXTERNAL_STORAGE'
def check_state(name,label):
    adb('shell','am','force-stop',package)
    adb('shell','am','start','-W','-n',f'{package}/.MainActivity')
    pid=adb('shell','pidof',package).strip().split()[0]
    for _ in range(20):
        adb('shell','uiautomator','dump','/sdcard/orbit-permissions.xml',check=False)
        xml=adb('exec-out','cat','/sdcard/orbit-permissions.xml',check=False)
        try:
            nodes=ET.fromstring(xml).iter('node')
            matches=[n for n in nodes if n.get('resource-id')==f'{package}:id/btnGrant' and n.get('text')==label]
        except ET.ParseError:matches=[]
        if matches:
            (qa/f'{name}.xml').write_text(xml)
            break
        time.sleep(1)
    else:raise RuntimeError(f'Missing permission recovery control: {name}')
    log=adb('logcat','-d',f'--pid={pid}',check=False)
    (qa/f'{name}.log').write_text(log)
    assert 'FATAL EXCEPTION' not in log,name
    assert adb('shell','pidof',package).strip(),name

try:
    adb('shell','pm','revoke',package,permission,check=False)
    if api>=34:adb('shell','pm','revoke',package,'android.permission.READ_MEDIA_VISUAL_USER_SELECTED',check=False)
    check_state('denied','ALLOW ACCESS')
    if api>=34:
        adb('shell','pm','grant',package,'android.permission.READ_MEDIA_VISUAL_USER_SELECTED')
        check_state('partial','ADD VIDEOS')
finally:
    adb('shell','pm','grant',package,permission)
print(f'PASS: API {api} permission recovery UI (partial state only; user reselection requires device QA)')
