#!/usr/bin/env python3
"""Record Android rendering/error evidence for generated media on one emulator/device."""
import json
from pathlib import Path
import re
import subprocess
import sys
import time

source=Path(sys.argv[1] if len(sys.argv)>1 else 'qa/media')
report=Path('qa/media-results');report.mkdir(parents=True,exist_ok=True)
package='com.orbitplayer.app'
def adb(*args,check=True):
    result=subprocess.run(['adb',*args],capture_output=True,text=True,timeout=120)
    if check and result.returncode:
        raise RuntimeError(f'adb {args!r} failed ({result.returncode}): {result.stdout}\n{result.stderr}')
    return result.stdout

# connectedAndroidTest uninstalls its target APK on completion. Each independent
# suite owns its installation and grants instead of relying on a previous suite.
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
api=int(adb('shell','getprop','ro.build.version.sdk').strip())
permission='android.permission.READ_MEDIA_VIDEO' if api>=33 else 'android.permission.READ_EXTERNAL_STORAGE'
adb('shell','pm','grant',package,permission)

samples=sorted(source.glob('*.mp4'))+sorted(source.glob('*.mkv'))+sorted(source.glob('*.webm'))
adb('shell','mkdir','-p','/sdcard/Movies/OrbitQA')
for sample in samples:
    adb('push',str(sample),f'/sdcard/Movies/OrbitQA/{sample.name}')
    adb('shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d',f'file:///sdcard/Movies/OrbitQA/{sample.name}')
rows={}
for _ in range(30):
    listing=adb('shell','content','query','--uri','content://media/external/video/media','--projection','_id:_display_name')
    rows={name.strip():number for number,name in re.findall(r'_id=(\d+), _display_name=([^\r\n]+)',listing)}
    if all(p.name in rows for p in samples if p.name!='corrupt.mp4'):break
    time.sleep(1)

results=[]
for sample in samples:
    # A malformed file may not be indexed. File URI still exercises the exported
    # failure route; all valid files must use an actual MediaStore row.
    if sample.name!='corrupt.mp4' and sample.name not in rows:
        raise RuntimeError(f'MediaStore failed to index {sample.name}')
    uri=f'content://media/external/video/media/{rows[sample.name]}' if sample.name in rows else f'file:///sdcard/Movies/OrbitQA/{sample.name}'
    adb('shell','am','force-stop',package)
    launch=adb('shell','am','start','-W','-a','android.intent.action.VIEW','-d',uri,'-t','video/*','-n',f'{package}/.PlayerActivity')
    process_ids=adb('shell','pidof',package,check=False).strip().split()
    if not process_ids:
        log=adb('logcat','-d','-v','brief',check=False)
        (report/f'{sample.name}.log').write_text(launch+'\n'+log)
        raise RuntimeError(f'Player did not stay running for {sample.name}; see qa/media-results/{sample.name}.log')
    pid=process_ids[0]
    log=''
    for _ in range(30):
        log=adb('logcat','-d','-v','brief',f'--pid={pid}',check=False)
        if 'FIRST_FRAME' in log or 'PLAYER_ERROR' in log or 'FATAL EXCEPTION' in log:break
        time.sleep(1)
    (report/f'{sample.name}.log').write_text(launch+'\n'+log)
    alive=bool(adb('shell','pidof',package,check=False).strip())
    rendered='FIRST_FRAME' in log
    error='PLAYER_ERROR' in log
    fatal='FATAL EXCEPTION' in log
    optional=sample.name in ('hevc.mp4','hevc.mkv','h264_ac3.mkv')
    passed=alive and not fatal and (error if sample.name=='corrupt.mp4' else (rendered or (optional and error)))
    result=dict(sample=sample.name,passed=passed,video_rendered=rendered,handled_error=error,
                audio_selected=re.findall(r'AUDIO_SELECTED ([^\s]+)',log),audible_audio_verified=False)
    results.append(result)
    print(json.dumps(result),flush=True)
(report/'results.json').write_text(json.dumps(results,indent=2)+'\n')
if not all(row['passed'] for row in results):sys.exit('Media matrix has a regression; see qa/media-results')
