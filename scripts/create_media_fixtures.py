#!/usr/bin/env python3
"""Deterministic synthetic media; generation/ffprobe is not Android decoder evidence."""
import json
from pathlib import Path
import subprocess
import sys

out = Path(sys.argv[1] if len(sys.argv)>1 else 'qa/media')
out.mkdir(parents=True,exist_ok=True)
def ff(name,*args):
    subprocess.run(['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-y',*args,str(out/name)],check=True)
base=['-f','lavfi','-i','testsrc2=size=320x240:rate=15','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','20']
h264=['-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p']
ff('h264_aac.mp4',*base,*h264,'-c:a','aac','-movflags','+faststart')
ff('h264.mkv','-i',str(out/'h264_aac.mp4'),'-c','copy')
for name in ['hevc.mp4','hevc.mkv']:
    ff(name,*base,'-c:v','libx265','-preset','ultrafast','-x265-params','log-level=error:pools=1:frame-threads=1','-pix_fmt','yuv420p','-tag:v','hvc1','-c:a','aac')
ff('vp9_opus.webm',*base,'-c:v','libvpx-vp9','-deadline','realtime','-cpu-used','8','-c:a','libopus')
ff('no_audio.mp4','-i',str(out/'h264_aac.mp4'),'-an','-c:v','copy')
ff('h264_ac3.mkv','-i',str(out/'h264_aac.mp4'),'-c:v','copy','-c:a','ac3')
ff('1080p.mp4','-f','lavfi','-i','testsrc2=size=1920x1080:rate=5','-t','12',*h264)
ff('long_1h.mp4','-f','lavfi','-i','color=c=navy:size=320x240:rate=1','-t','3600',*h264,'-movflags','+faststart')
ff('vfr.mp4','-f','lavfi','-i','testsrc2=size=320x240:rate=5:duration=8','-f','lavfi','-i','testsrc2=size=320x240:rate=15:duration=8',
   '-filter_complex','[0:v][1:v]concat=n=2:v=1:a=0[v]','-map','[v]','-fps_mode','vfr',*h264)
(out/'captions.srt').write_text('1\n00:00:01,000 --> 00:00:09,000\nOrbit subtitle fixture\n\n2\n00:00:10,000 --> 00:00:18,000\nSecond cue\n')
(out/'captions.vtt').write_text('WEBVTT\n\n00:01.000 --> 00:09.000\nOrbit VTT fixture\n\n00:10.000 --> 00:18.000\nSecond cue\n')
(out/'captions.ass').write_text('''[Script Info]
ScriptType: v4.00+
PlayResX: 320
PlayResY: 240
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,12,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:18.00,Default,,0,0,0,,Orbit ASS fixture
''')
ff('multi_audio_subs.mkv','-i',str(out/'h264_aac.mp4'),'-f','lavfi','-i','sine=frequency=880:sample_rate=48000','-i',str(out/'captions.srt'),
   '-map','0:v','-map','0:a','-map','1:a','-map','2:s','-t','20','-c:v','copy','-c:a','aac','-c:s','srt',
   '-metadata:s:a:0','language=eng','-metadata:s:a:1','language=hin','-metadata:s:s:0','language=eng')
(out/'corrupt.mp4').write_bytes(b'Orbit corrupt media fixture: deliberately not a container')
manifest={}
for p in sorted(out.iterdir()):
    if p.suffix in ('.mp4','.mkv','.webm') and p.name!='corrupt.mp4':
        probe=subprocess.run(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(p)],capture_output=True,text=True,check=True)
        manifest[p.name]=json.loads(probe.stdout)
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(f'Generated {len(manifest)} valid containers, corrupt sample and external subtitle fixtures in {out}')
