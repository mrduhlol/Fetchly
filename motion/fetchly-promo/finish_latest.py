"""Master the current-UI HyperFrames render and record verified delivery metadata."""
from pathlib import Path
import hashlib, json, re, subprocess
ROOT=Path(__file__).resolve().parent
MASTER=ROOT/'renders/fetchly-latest-master.mp4'
OUTPUT=ROOT/'renders/fetchly-latest-1080p.mp4'
def measure(path):
 p=subprocess.run(['ffmpeg','-hide_banner','-i',str(path),'-vn','-af','loudnorm=I=-16:TP=-2:LRA=9:print_format=json','-f','null','-'],capture_output=True,text=True,check=True)
 return json.loads(re.search(r'\{\s*"input_i".*?\}',p.stderr,re.S).group())
def main():
 d=measure(MASTER)
 chain=('loudnorm=I=-16:TP=-2:LRA=9:measured_I='+d['input_i']+':measured_TP='+d['input_tp']+':measured_LRA='+d['input_lra']+':measured_thresh='+d['input_thresh']+':offset='+d['target_offset']+':linear=false,alimiter=limit=0.5:level=false:attack=5:release=50:latency=true,volume=1.3dB')
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(MASTER),'-map','0:v:0','-map','0:a:0','-c:v','copy','-af',chain,'-c:a','aac','-b:a','192k','-ar','48000','-movflags','+faststart','-metadata','title=Fetchly - Paste. Choose. Fetch.','-metadata','artist=mrduhlol','-y',str(OUTPUT)],check=True)
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(OUTPUT)]))
 video=next(s for s in probe['streams'] if s['codec_type']=='video');audio=next(s for s in probe['streams'] if s['codec_type']=='audio')
 assert(video['width'],video['height'],video['r_frame_rate'])==(1920,1080,'30/1')
 assert abs(float(probe['format']['duration'])-55.0)<.1
 assert int(video['nb_frames'])==1650
 loud=measure(OUTPUT); assert float(loud['input_tp'])<-2
 (ROOT/'LATEST-DELIVERY.json').write_text(json.dumps({'file':str(OUTPUT.relative_to(ROOT)),'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),'bytes':OUTPUT.stat().st_size,'duration_seconds':float(probe['format']['duration']),'width':video['width'],'height':video['height'],'fps':video['r_frame_rate'],'frames':int(video['nb_frames']),'video_codec':video['codec_name'],'audio_codec':audio['codec_name'],'audio_sample_rate':audio['sample_rate'],'integrated_lufs':float(loud['input_i']),'true_peak_dbtp':float(loud['input_tp']),'subtitles':'burned-in + fetchly-latest.srt','sound_review':'Measured; not human-listened.'},indent=2)+'\n')
 print(OUTPUT);print(loud['input_i']+' LUFS / '+loud['input_tp']+' dBTP')
if __name__=='__main__':main()
