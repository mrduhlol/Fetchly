# Validation

- HyperFrames 0.9.20 final-source check passed: zero lint warnings/errors, zero runtime/layout/craft findings. SVG outline contrast was inspected visually, not measured by the text audit.
- Reviewed rendered portrait frames across awakening, lettering, laurel growth, loading and fade, including full-canvas source snapshots and phone-size review sheets.
- `./gradlew :app:compileDebugKotlin --console=plain`: BUILD SUCCESSFUL. Existing Gradle deprecation notice and Compose's deprecated `quadraticBezierTo` alias warning remain; no compiler errors.
- Native readiness/fade path inspected in source. No six-second minimum; preference readiness starts a 350 ms crossfade; no timed release of an unready app.
- Android device discovery did not return. No device runtime, frame pacing or transition recording was completed. Native on-device performance is not asserted.
- Master probed with FFprobe: H.264, yuv420p, 2160 × 3840, 60/1 FPS, 396 frames, 6.600000 seconds. Encoded frames decoded for visual review. Preview is downsampled from this master, not enlarged.
- Original Apollo reference remains unmodified. Derived Apollo has genuine RGBA transparency and is displayed below native dimensions in the master.
- No device frames, borders or watermarks. Video contains no audio.

- Preview probe: H.264 yuv420p, 1080 × 1920, 60/1 FPS, 396 frames, 6.600000 s. First and last encoded master frames both have uniform limited-range black luma (YAVG = YMAX = 16).
- Inline HTML player preview failed: “Headless Chrome could not render the page: the page did not finish loading within 20 seconds.” Downloadable MP4s and HyperFrames rendering succeeded.
