"""Generate requested local narration using cached HyperFrames Kokoro assets.
Run with Python containing kokoro-onnx, onnxruntime, numpy and soundfile.
No credentials or provider calls are used. Model downloads are managed by hyperframes tts.
"""
from pathlib import Path
import json
import onnxruntime as ort
from kokoro_onnx import Kokoro
import soundfile as sf

ROOT = Path(__file__).resolve().parent
CACHE = Path.home() / '.cache/hyperframes/tts'
LINES = [
    ('frustration', 'An ad before the download. And after all that... this is the quality you get?', 1.0),
    ('reveal', 'There is a better way. Fetchly.', 0.95),
    ('home', 'Start with a public link. Choose your format and quality. Save it right on your phone.', 1.0),
    ('platforms', 'Bring links from the platforms you use. Public media, ready to fetch.', 1.0),
    ('share', 'Found something worth keeping? Tap Share. Choose Fetchly. Your download options open right over the app. Pick your quality, and download. Stay in the moment.', 1.02),
    ('features', 'Save audio as MP3. Review multiple links. Pause and resume supported downloads.', 1.0),
]

def main():
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(CACHE/'models/kokoro-v1.0.onnx'), sess_options=options, providers=['CPUExecutionProvider'])
    model = Kokoro.from_session(session, str(CACHE/'voices/voices-v1.0.bin'))
    output = ROOT/'assets/voice'
    output.mkdir(parents=True, exist_ok=True)
    metadata = []
    for name, text, speed in LINES:
        path = output/f'{name}.wav'
        if path.exists():
            info = sf.info(path)
            duration, rate = info.duration, info.samplerate
        else:
            samples, rate = model.create(text, voice='am_michael', speed=speed, lang='en-us')
            sf.write(path, samples, rate, subtype='PCM_16')
            duration = len(samples)/rate
        metadata.append(dict(id=name, text=text, provider='kokoro-local', voice='am_michael', speed=speed, path=str(path.relative_to(ROOT)), duration=round(duration, 3), sample_rate=rate))
        print(f'{name}: {duration:.3f}s', flush=True)
    (ROOT/'narration.json').write_text(json.dumps(metadata, indent=2)+'\n')

if __name__ == '__main__':
    main()
