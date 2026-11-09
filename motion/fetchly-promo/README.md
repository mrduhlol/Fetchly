# Fetchly — Keep the Moment

A 64-second, 1920×1080 / 30 fps HyperFrames promo with original Three.js phone/hand scenes, local Kokoro narration, and the six Pixabay audio originals supplied by the user.

Final local file: `renders/fetchly-promo-1080p.mp4` (ignored build output). Exact codec, duration, loudness and SHA-256: [DELIVERY.json](DELIVERY.json). See [REVIEW.md](REVIEW.md) for the measured checks and visual review, and [CREDITS.md](CREDITS.md) for source/publication credits.

No stock video clips are used. Home and format screens are actual Android captures. The share flow is a disclosed reconstruction from the native app source because device capture was unavailable. Phone, hand, camera motion and smoke are locally rendered; no provider watermark is added.

## Rebuild

The supplied original MP3s stay in ignored `assets/audio/pixabay/` as `pop.mp3`, `whoosh.mp3`, `click.mp3`, `impact.mp3`, `rhythm.mp3` and `lyre.mp3`. Preserve those local files when moving the project. They are not redistributed standalone in Git.

```bash
python build_composition.py
npm run check
npm run dev
npm run render -- --quality delivery --fps 30 --workers 1 --no-browser-gpu --strict --no-best-effort --output renders/fetchly-promo-master.mp4
python finish_delivery.py
```

The eight scenes in `compositions/` remain editable. The root holds independent voice, music and SFX tracks. `finish_delivery.py` preserves the video stream and masters the audio for the final fast-start MP4. It requires FFmpeg. Narration generation is optional: `generate_narration.py` uses local Kokoro models cached by HyperFrames and Python packages kokoro-onnx, onnxruntime, numpy and soundfile.
