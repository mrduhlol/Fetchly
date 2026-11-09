from pathlib import Path
import json, re, shutil, tempfile, numpy as np, soundfile as sf, onnxruntime as ort
from kokoro_onnx import Kokoro
ROOT=Path(__file__).resolve().parent
OUT=Path(tempfile.mkdtemp(prefix='fetchly-promo-voice-'))
LINES=[
('hook', 'See something worth keeping? Make it yours with Fetchly.'),
('paste', 'Paste a public link. Preview the media, then choose your format and quality.'),
('audio', 'Want just the sound? Switch to audio and choose your MP3 bitrate.'),
('share', 'Or share a link straight to Fetchly. Your download options open over the app.'),
('batch', 'More than one link? Review them together, then choose what to queue.'),
('library', 'Follow your downloads, and find your saved media in one library.'),
('appearance', 'Make Fetchly your own, with gold and white themes and adjustable app size.'),
('end', 'No account. No backend. Processing stays on your phone. Download Fetchly from my GitHub repository. Paste. Choose. Fetch.')]
opt=ort.SessionOptions(); opt.intra_op_num_threads=2; opt.inter_op_num_threads=1
cache=Path.home()/'.cache/hyperframes/tts'
model=Kokoro.from_session(ort.InferenceSession(str(cache/'models/kokoro-v1.0.onnx'),sess_options=opt,providers=['CPUExecutionProvider']),str(cache/'voices/voices-v1.0.bin'))
meta=[]
for name,text in LINES:
 parts=re.split(r'(?<=[.?!])\s+',text)
 audio_parts=[]; cues=[]; position=0
 for i,part in enumerate(parts):
  p=OUT/f'{name}-sentence-{i}.wav'
  if p.exists():
   audio,rate=sf.read(p)
  else:
   audio,rate=model.create(part,voice='am_michael',speed=1.0,lang='en-us');sf.write(p,audio,rate,subtype='PCM_16')
  duration=len(audio)/rate
  cues.append({'text':part,'start':round(position,4),'end':round(position+duration,4)})
  audio_parts.extend([audio,np.zeros(int(rate*.14))]);position+=duration+.14
 path=OUT/f'{name}-final.wav';sf.write(path,np.concatenate(audio_parts),rate,subtype='PCM_16')
 meta.append({'id':name,'text':text,'duration':round(position,4),'path':f'assets/voice/latest-{name}.wav','provider':'kokoro-local','voice':'am_michael','cues':cues})
 print(name,round(position,3),flush=True)
for entry in meta:
 shutil.copyfile(OUT/(entry['id']+'-final.wav'),ROOT/entry['path'])
(ROOT/'latest-narration.json').write_text(json.dumps(meta,indent=2)+'\n')
shutil.rmtree(OUT)
