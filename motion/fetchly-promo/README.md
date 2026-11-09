# Fetchly — current-UI promo

55.0-second HyperFrames promo, 1920 × 1080 at 30 fps, with fresh captures from the current full Android app, local narration, burned-in subtitles, music and sound effects. The final card and voice invite viewers to download the APK from `github.com/mrduhlol/Fetchly`.

Delivery: `renders/fetchly-latest-1080p.mp4` (local ignored render), with portable subtitles in `fetchly-latest.srt`. See `LATEST-DELIVERY.json`, `LATEST-REVIEW.md`, `LATEST-ASSETS.md` and `LATEST-SPEC.md` for validation and provenance.

## Rebuild

```bash
python build_latest.py
npm run check
npx --yes hyperframes@0.9.26 render --quality delivery --fps 30 --workers 1 --no-browser-gpu --strict --no-best-effort --output renders/fetchly-latest-master.mp4
python finish_latest.py
```

All eight scenes and the caption composition remain editable. Source captures and generated narration are versioned. Optional narration regeneration: run `generate_latest_narration.py` with the existing Kokoro model cache and Python packages kokoro-onnx, onnxruntime, numpy and soundfile.

The six user-supplied Pixabay originals remain local in ignored `assets/audio/pixabay/`; preserve them when moving the project. Credits: `CREDITS.md` and `PIXABAY_SOURCES.md`. They are not redistributed as standalone tracks in Git.

The preceding 64-second film and its build script remain available in repository history. The root composition now mounts only the current-UI scenes.
