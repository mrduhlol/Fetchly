# Asset provenance

- assets/screens/home-dark.png: captured from installed full Fetchly debug app (com.fetchly.app.debug), Android 14 emulator fetchly_test, through the bound agent-device screenshot command. 720×1600 original capture. Not a recreated product screen. Status/navigation bars remain in source; crop only for presentation.
- assets/brand/download-apk.svg: exact copy of repository docs/download-apk.svg, as requested. Download target: https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly.apk.
- assets/brand/fetchly-logo.png: exact copy of app/src/main/res/drawable-nodpi/fetchly_logo.png.
- compositions/components/browser-device-stage.html: HyperFrames registry component, installed with hyperframes add. Retained as a source reference; not yet mounted.

## Production state

Storyboard, specification, working narration and first real screen captured. Composition is still the scaffold, not a completed film. No delivery render exists. Audio provider choice is pending: HeyGen sign-in or explicit acceptance of free local model downloads. No paid media calls have been made.

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
