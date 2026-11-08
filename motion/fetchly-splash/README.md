# FETCHLY — Apollo awakening

Editable portrait motion composition: 2160 × 3840, 60 fps, 6.6 s (396 frames), silent.

## Deliverables

- `exports/fetchly-apollo-4k60.mp4`: full-resolution H.264 master.
- `exports/fetchly-apollo-1080p60.mp4`: 1080 × 1920 H.264 preview.
- `assets/`: independent Apollo RGBA PNG, outlined FETCHLY SVG/PNG, open laurel SVG/PNG, native 4K background, rays, particles and controllable loading-bar SVG/PNG.
- `index.html`, `compositions/splash.html`: editable HyperFrames/GSAP source. GSAP is local; renders need no asset CDN.
- `build_assets.py`, `build_composition.py`: reproducible vector artwork and timeline builders. Edit the builders if you intend to regenerate; otherwise edit the composition directly.
- Android implementation: `../../app/src/main/java/com/fetchly/app/ui/splash/FetchlySplash.kt`, integrated in `MainActivity.kt`.

## Timeline

| Time | Motion |
| --- | --- |
| 0–2 s | Absolute black first frame; warm lighting rises; Apollo ascends 240 canvas pixels with a power-four deceleration and opacity reveal. |
| 2–3.2 s | Seven outlined Cinzel letters reveal left to right, 85 ms apart. Gold sweep follows the glyph silhouettes. |
| 3.2–4.2 s | Mirrored stems draw from the centre gap; nine pairs of individually shaped leaves per branch reveal outward. A masked shimmer completes the divider. |
| 4.2–6 s | Thin progress line appears and fills; a travelling highlight follows its head. Brand and figure remain stationary. |
| 6–6.55 s | Composition fades to black for the standalone video. In the app, its alpha fades over the actual destination screen whenever ready. |
| 6.55–6.6 s | Black tail for a clean video endpoint. |

## Resolution and preservation

The supplied reference is the 1280 × 1280 `fetchly_logo.png`. The built-in imagegen tool extracted a 1254 × 1254 RGBA Apollo cutout. It visually follows the original face, hair, rays, arrows, drapery and brooch, but generative extraction is **not a pixel-identical conservation process**. Original art remains untouched. See `ASSET_PROVENANCE.md` for the exact prompt.

Apollo displays at **1220 × 1220** in the 2160 × 3840 master, including its transparent margins: it is not enlarged past native resolution. Typography and ornaments are vector paths; lighting and background are rendered on the native 4K canvas. This is a layered animation of the supplied sculptural illustration, not a newly modelled 3D statue. The laurel has two separate branches with a 30-unit centre gap and no enclosing path.

## Edit and render

Node 22+ and FFmpeg are required. The package scripts pin HyperFrames 0.9.20.

```sh
npm run dev
npm run check
npx hyperframes@0.9.20 snapshot --at 0,1,2.8,3.8,5.5,6.3
npx hyperframes@0.9.20 render --fps 60 --quality delivery --crf 14 --workers 1 --output exports/fetchly-apollo-4k60.mp4
ffmpeg -i exports/fetchly-apollo-4k60.mp4 -vf scale=1080:1920:flags=lanczos -c:v libx264 -crf 16 -preset slow -pix_fmt yuv420p -movflags +faststart -an exports/fetchly-apollo-1080p60.mp4
```

To rebuild artwork, install `fonttools cairosvg pillow` in a Python virtual environment, then run `build_assets.py` and `build_composition.py`. Cinzel is included under the SIL Open Font License (`assets/fonts/OFL.txt`). The wordmark uses outlines and does not depend on installed fonts.

## Android behaviour

The app uses native Compose Canvas, two lossless WebP resources, and vector leaf paths. It does **not** package either video or the 4K lighting PNGs. Composition coordinates scale uniformly to fit any screen, with black extending beyond the 9:16 canvas; no stretching or phone frame.

Startup readiness is the first loaded welcome preference plus the first loaded theme preference, the existing screen-routing dependencies. AppGraph is already constructed before Compose starts. Notification permission waits until the splash has dismissed. Returning users crossfade to FetchlyNav; first-time users retain the existing WelcomeScreen flow. A restored Activity skips the intro.

`FetchlySplash(ready, progress, onFinished)` accepts optional real normalized progress. This app exposes no measurable startup work total, so production uses `progress = null`: a travelling light on an unfilled track, **not fabricated progress**. The video has a deterministic demonstration fill. Additional mandatory startup tasks should be combined into `ready`; pass a measured completed/total ratio only if one exists.

Readiness interrupts every scene immediately and starts a 350 ms fade after a destination composition frame. There is no six-second minimum, no timer that releases an unready app, and no extra network request. Fast launches may show only the opening black-to-light frames; that is intentional. Unknown initialization can hold the composition after six seconds. The production artwork is a lightweight native adaptation: text wipes progressively and lighting is simplified; it does not reproduce the offline blur and every shimmer pixel for pixel.

Android 12+ keeps its mandatory system splash, using a black background and transparent system icon so the app's animation has a clean beginning. Earlier versions use a plain black window background. The existing launcher icon is unchanged.

The standalone MP4 ends on black because it cannot know the user's live home state; the actual screen transition belongs to the integrated native implementation. Do not play the MP4 as a startup gate.
