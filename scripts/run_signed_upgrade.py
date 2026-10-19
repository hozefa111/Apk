#!/usr/bin/env python3
"""Internal API26 emulator upgrade. Never publishes or changes production data.
Collections are deterministic legacy-format fixtures; progress/history originate
from actual stable playback. This is not a physical-device migration claim.
"""
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

package='com.orbitplayer.app'
report=Path('qa/upgrade')
report.mkdir(parents=True,exist_ok=True)
def adb(*args,raw=False,stdin=None,check=True):
    r=subprocess.run(['adb',*args],input=stdin,capture_output=True,timeout=120)
    if check and r.returncode:raise RuntimeError(f'adb {args!r}: {r.stderr.decode(errors="replace")} {r.stdout.decode(errors="replace")}')
    return r.stdout if raw else r.stdout.decode()
def prefs(name):
    return adb('shell','cat',f'/data/user/0/{package}/shared_prefs/{name}.xml')
def values(xml):
    result={}
    for node in ET.fromstring(xml):
        result[node.get('name')]=sorted(c.text for c in node) if node.tag=='set' else node.get('value',node.text)
    return result

adb('root')
adb('wait-for-device')
assert adb('shell','id').startswith('uid=0'), 'Fresh root-capable test emulator required'
adb('install',str(report/'stable.apk'))
adb('shell','pm','grant',package,'android.permission.READ_EXTERNAL_STORAGE')
adb('shell','mkdir','-p','/sdcard/Movies')
adb('push',str(report/'upgrade.mp4'),'/sdcard/Movies/upgrade.mp4')
adb('shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Movies/upgrade.mp4')
import re
uri=None
for _ in range(30):
    rows=adb('shell','content','query','--uri','content://media/external/video/media','--projection','_id:_display_name')
    match=re.search(r'_id=(\d+), _display_name=upgrade.mp4',rows)
    if match:
        uri='content://media/external/video/media/'+match.group(1);break
    time.sleep(1)
assert uri,'Stable media not indexed'
def launch():
    adb('shell','am','start','-W','-a','android.intent.action.VIEW','-d',uri,'-t','video/*','-n',package+'/.PlayerActivity')
launch()
time.sleep(10)
adb('shell','input','keyevent','KEYCODE_HOME')
time.sleep(2)
adb('shell','am','force-stop',package)
# Root is restricted to this disposable emulator, enabling fixtures for a
# non-debuggable stable APK without changing either APK's signing identity.
xml=ET.Element('map')
favs=ET.SubElement(xml,'set',name='favorites');ET.SubElement(favs,'string').text=uri
ET.SubElement(xml,'string',name='playlists').text=json.dumps({'Upgrade QA':[uri]})
folder=f'/data/user/0/{package}/shared_prefs'
owner=adb('shell','stat','-c','%u',folder).strip()
path=folder+'/orbit_collections_v1.xml'
adb('shell',f'cat > {path}',stdin=ET.tostring(xml))
adb('shell','chown',owner+':'+owner,path)
adb('shell','chmod','660',path)
# Preserve the app's SELinux category as well as its UID on enforcing emulators.
label=adb('shell','ls','-Z',folder+'/playback_positions_v1.xml').split()[0]
adb('shell','chcon',label,path)
before={n:values(prefs(n)) for n in ('orbit_collections_v1','playback_positions_v1')}
assert any(k.startswith('position_') and int(v)>0 for k,v in before['playback_positions_v1'].items()), 'Stable did not save actual playback position'
assert before['playback_positions_v1'].get('recently_watched'),'Stable history missing'
adb('install','-r','app/build/outputs/apk/release/app-release.apk')
metadata=adb('shell','dumpsys','package',package)
assert 'versionCode=14' in metadata and 'versionName=0.3.1' in metadata
(report/'package-after-update.txt').write_text(metadata)
after={n:values(prefs(n)) for n in before}
assert before==after,'Install changed legacy collections/progress/history'
# Launch the upgraded optimized build so preferences are actually consumed.
adb('logcat','-c',check=False)
launch()
time.sleep(5)
pid=adb('shell','pidof',package).strip()
assert pid,'Candidate exited'
log=adb('logcat','-d','-v','brief',f'--pid={pid}')
assert 'FATAL EXCEPTION' not in log, 'Optimized candidate crashed'
(report/'optimized-playback.txt').write_text(log)
(report/'optimized-playback.png').write_bytes(adb('exec-out','screencap','-p',raw=True))
# Saved progress must advance beyond the position written by stable playback.
adb('shell','input','keyevent','KEYCODE_HOME')
time.sleep(2)
adb('shell','am','force-stop',package)
final={n:values(prefs(n)) for n in before}
assert final['orbit_collections_v1']==before['orbit_collections_v1'],'Candidate changed collections'
positions=[k for k in before['playback_positions_v1'] if k.startswith('position_')]
assert all(int(final['playback_positions_v1'][k])>int(before['playback_positions_v1'][k]) for k in positions),'Optimized playback failed to resume and advance'
(report/'result.json').write_text(json.dumps(dict(passed=True,stable_version='0.3.0',candidate_version='0.3.1',same_certificate_install=True,collections_fixture_preserved=True,actual_stable_resume_and_history_preserved=True,optimized_playback_advanced=True,physical_device_verified=False),indent=2)+'\n')
print('PASS: signed in-place update, legacy collections, real stable resume/history and optimized playback')
