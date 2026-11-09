# Asset provenance

- assets/screens/home-dark.png: captured from installed full Fetchly debug app (com.fetchly.app.debug), Android 14 emulator fetchly_test, through the bound agent-device screenshot command. 720×1600 original capture. Not a recreated product screen. Status/navigation bars remain in source; crop only for presentation.
- assets/brand/download-apk.svg: exact copy of repository docs/download-apk.svg, as requested. Download target: https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly.apk.
- assets/brand/fetchly-logo.png: exact copy of app/src/main/res/drawable-nodpi/fetchly_logo.png.
- compositions/components/browser-device-stage.html: HyperFrames registry component, installed with hyperframes add. Retained as a source reference; not yet mounted.

## Production state

Completed: the 64-second film has been rendered and verified. Local narration and user-supplied Pixabay audio are used. See DELIVERY.json for the output hash, codec, timing and measured levels. Earlier provider/access notes below are production history; no HeyGen audio is used in the final film.

## Audio retrieval and account limitation

- assets/audio/greek-lyre-bed.wav: retrieved through HeyGen audio catalog using the user's selected OAuth route. Track ID 12991e9002df42c193a0901659561227; description: dramatic ancient Greek ambient, lyre and soft percussion, tragic and poetic; source duration 55 s. No visual media or baked-in video watermark is involved. The final mix and licence metadata still need review before delivery.
- Narration attempts returned exactly: "Choose a HyperFrames plan to keep building". No narration files were generated. The user must choose a suitable plan or explicitly choose local narration/model downloads before audio production resumes.
- Watermark-free delivery is required. No finished composition or rendered film exists yet.

## Local narration, approved by user

- assets/voice/*.wav: six original locally generated Kokoro-82M takes; voice am_michael, English US, PCM16 at 24000 Hz. Metadata and exact spoken text: narration.json. Generation source: generate_narration.py. No provider video or watermark involved. Sound is measured, not human-listened.
- assets/screens/home-enabled.png: unmodified capture of the real Fetchly home screen after entering the public Big Buck Bunny YouTube link. Captured through the bound agent-device command.
- Music/SFX provider has changed to Pixabay at the user’s instruction. The earlier HeyGen music source is excluded from the planned delivery.
- Pixabay page fetch/download returned HTTP 403. Shared browser failed with a transport error, then a 15000 ms open timeout. Standard Chromium startup then failed waiting 30000 ms for its WS endpoint. No CAPTCHA or access restriction was bypassed. No Pixabay audio files have been downloaded.
- assets/screens/quality-video.png: real Fetchly video-quality screen after resolving the official Big Buck Bunny public YouTube link. No invented quality values or UI. This is an application state, not downloaded video footage; any future use of the film itself must carry its source licence.

## Supplied soundtrack received and film built

The user attached all six Pixabay MP3 originals. They are copied into ignored assets/audio/pixabay/ using short names pop, whoosh, click, impact, rhythm and lyre. See CREDITS.md for original titles and page URLs. MP3 durations were verified with ffprobe. Measured peaks: pop .202s, click .074s, riser-impact 1.815s; picture contacts are aligned to these peaks plus one frame. Original standalone audio is not committed to the public repo.

The 64-second composition is built by build_composition.py. Phone/hand geometry is original Three.js; smoke is a deterministic generated canvas effect. No stock video clips are used. ShareDownloadScreen.kt supplies exact shared-dialog labels and behaviour; because the emulator was blocked by system ANR and device tooling became unavailable, the share sequence is reconstructed and disclosed on screen. Runtime and layout checks plus rendered-frame review precede delivery.

## Delivered output

Final: renders/fetchly-promo-1080p.mp4, 64.000 seconds, 1920×1080, 30 fps, 1920 H.264 frames, stereo AAC 48 kHz. Final audio measures −16.0 LUFS and −2.8 dBFS true peak. Eleven frames were decoded from the MP4 and visually checked. The unused earlier HeyGen WAV was removed from the current project. The original standalone Pixabay MP3s and rendered MP4 are kept locally in ignored paths. Source and validation notes are committed and pushed.
