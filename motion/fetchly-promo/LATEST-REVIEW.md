# Current-UI promo review

The 55-second, 1920×1080 / 30 fps film follows a public link through format choices, MP3, native share, batch review and saved media, then shows appearance controls and holds the exact GitHub download invitation. Actual app fonts, Apollo artwork and bronze/slate colours carry the visual identity. The old frustration/ads story and a link-relay alternative were not used. No invented app controls or social-app screens appear.

Reviewed the scene beat sheet, phone-width sheet, transition strips, and revised library/end-card frames. Corrected the undersized supporting labels, the nearly empty end-card entrance and the library's initial animation baseline. Headlines stop after their entrance; screen moves reveal actual controls. The final CTA deliberately holds so viewers can read `github.com/mrduhlol/Fetchly` and find Releases.

The last completed full HyperFrames check passed lint, runtime, layout, motion, contrast and audio with zero errors and zero craft warnings. The remaining caption-density advisory is intentional: one caption composition holds all 19 sentence cues on one editable caption track. The library initial-state advisory was then corrected with a CSS baseline and an immediate initial GSAP set. Final validation is repeated after that correction. Python sources compile and Git whitespace checks pass.

Sentence captions use the exact durations of individually synthesized takes, with 140 ms between sentences. Audio is measured, not human-listened. Music and action effects are mixed below narration; final measured loudness, codecs, frame count and checksum are recorded by `finish_latest.py` in `LATEST-DELIVERY.json`. The MP4 is checked after encoding.

## Capture scope and limitation

Fresh captures show the full v2.4.7 UI: bundled Cinzel/Manrope, current Home controls, dock, native share thumbnail, batch review, real saved image files, and theme/size controls. The APK's full-UI source baseline was `d2c098c`; SHA-256 `95c0c5bff80687eed26ebea0aa7cf57d0ce40a89e4457d8be4893bc8c03f3102`. Source app content has not been redrawn or composited over newer artwork.

Further Downloads artwork and theme revisions landed concurrently after the capture build. The attempted rebuild failed with `Timeout waiting to lock Build Output Cleanup Cache`; the device connection subsequently failed twice with `Could not communicate with device support`. Those later artwork revisions are consequently not represented by these captures. The clip accurately shows the captured build rather than claiming to show those concurrent edits.

The share dialog was invoked through an actual Android ACTION_SEND intent over the emulator launcher; it is not a recording of a user opening another social app's Share menu. The library shot is the weakest detail view because two full portrait captures trade small native text for the overall interface and wallpaper. Main headlines, subtitles and the end-card URL remain large.

Rendered output is ignored build output, retained locally. All editable source, source captures, generated narration and the portable SRT are versioned; unrelated application edits remain untouched.
