"""Finish a HyperFrames master with two-pass loudness, transient headroom and fast-start MP4."""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parent
MASTER = ROOT / 'renders/fetchly-promo-master.mp4'
OUTPUT = ROOT / 'renders/fetchly-promo-1080p.mp4'

def main():
    first = subprocess.run(['ffmpeg','-hide_banner','-i',str(MASTER),'-vn','-af',
        'loudnorm=I=-16:TP=-2:LRA=9:print_format=json','-f','null','-'],
        capture_output=True,text=True,check=True)
    match = re.search(r'\{\s*"input_i".*?\}',first.stderr,re.S)
    if not match:
        raise RuntimeError('No loudness measurements returned')
    d = json.loads(match.group())
    chain = ('loudnorm=I=-16:TP=-2:LRA=9:measured_I='+d['input_i']+
        ':measured_TP='+d['input_tp']+':measured_LRA='+d['input_lra']+
        ':measured_thresh='+d['input_thresh']+':offset='+d['target_offset']+
        ':linear=false,alimiter=limit=0.5:level=false:attack=5:release=50:latency=true,volume=1.3dB')
    temp = OUTPUT.with_name('fetchly-promo-finishing.mp4')
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(MASTER),
        '-map','0:v:0','-map','0:a:0','-c:v','copy','-af',chain,'-c:a','aac','-b:a','192k',
        '-ar','48000','-movflags','+faststart','-metadata','title=Fetchly - Paste. Choose. Fetch.',
        '-metadata','artist=mrduhlol','-y',str(temp)],check=True)
    temp.replace(OUTPUT)
    print(OUTPUT)

if __name__ == '__main__':
    main()
