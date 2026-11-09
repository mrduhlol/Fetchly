# Current-UI capture provenance

All `assets/screens/latest-*.png` files are unmodified 720×1600 PNG captures from the full Fetchly app built from current repository source, version 2.4.7, installed as `com.fetchly.app.debug.test` on Android 14 emulator `fetchly_test`. Captured through the T3-bound agent-device command with normalized system chrome. Their presentation uses rectangular crops and deterministic camera moves; no UI was redrawn. The test suffix is a build identifier, not a different interface.

- Home: current bundled Cinzel and Manrope fonts, translucent controls, platform icons and navigation dock.
- Quality: real resolution of the official Big Buck Bunny YouTube link, with the source-reported 2160p MP4 option.
- Audio: actual MP3 controls and 320 kb/s selection; file sizes are source estimates.
- Share: actual native ShareDownloadActivity, launched with an Android ACTION_SEND intent containing the same public link. The emulator launcher is underneath; this is a direct intent capture, not a recorded social-app sharing interaction.
- Batch: actual successful review of two gstatic WebP sample image URLs, with both items selected.
- Downloads/Library: the same two samples genuinely saved by Fetchly, with searchable history and real thumbnails.
- Appearance/Size: actual theme choices and app-size settings.

`assets/voice/latest-*.wav`: new local Kokoro am_michael narration, selected by the user. Generated sentence by sentence, with 140 ms of silence between takes; measured PCM sample durations define exact sentence subtitle boundaries. Full text and cue timing are in `latest-narration.json`. `fetchly-latest.srt` carries the same cues as the burned-in subtitle composition.

Music and SFX reuse the six existing user-supplied Pixabay originals. See CREDITS.md and PIXABAY_SOURCES.md. No HeyGen media or account credits are used. The registry caption source was consulted for structure; the finished caption treatment uses the app’s Manrope font and a compact bronze-underlined scrim.

Big Buck Bunny thumbnail: Blender Foundation, used as real source-preview UI, CC BY 3.0 (https://peach.blender.org/about/). Public image samples: https://developers.google.com/speed/webp/gallery; source links shown by Fetchly, no standalone sample footage added.
