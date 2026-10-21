# Downloader verification — 2026-10-21

The unit suite exercises URL validation and platform routing, extractor JSON mapping,
quality pairing, queue/view-model behavior, cancellation, storage, and error messages.
The Python bridge suite covers single-format extractors, multi-video selection,
missing codecs, thumbnails, regional restrictions, and structured storage failures.
Run `scripts/check.sh` with JDK 17, Python 3.12 with yt-dlp 2026.08.19
(`pip install yt-dlp==2026.08.19`) and the Android SDK installed.
The checked-in Gradle wrapper pins Gradle 8.7. CI runs tests, lint and an APK build
on pushes and pull requests and uploads the reports and APK.

Local result: **159 Kotlin unit tests and 11 Python bridge tests passed**; the debug
APK assembled successfully.

## Live extraction checks

Tests used the bundled yt-dlp version 2026.08.19 with JavaScript runtimes disabled,
on the development machine's connection in India. These are observations for the
specific public posts, not a guarantee that every post or network works.

- YouTube: `https://www.youtube.com/watch?v=jNQXAC9IVRw` — title, thumbnail,
  24 raw formats; an M4A audio download completed (117,454 bytes).
- Instagram: `https://instagram.com/p/aye83DjauH/` — title, thumbnail, 4 formats.
- LinkedIn: `https://www.linkedin.com/posts/mishalkhawaja_sendinblueviews-toronto-digitalmarketing-ugcPost-6850898786781339649-mM20`
  — title, thumbnail, 3 formats.
- Pinterest: `https://www.pinterest.com/pin/664281013778109217/` — title,
  thumbnail, 6 formats.
- X: a public Captain America extractor fixture — thumbnail and 4 formats.
- Reddit: `https://www.reddit.com/r/videos/comments/6rrwyj/that_small_heart_attack/`
  — source required account authentication on this connection.
- TikTok: `https://www.tiktok.com/@leenabhushan/video/6748451240264420610`
  — redirected to `https://www.tiktok.com/in/about`. Fetchly now identifies this
  regional restriction rather than reporting an unexplained webpage parsing error.

No credentials or third-party download proxy are used. Authenticated/private media,
DRM, regional restrictions and bot checks remain source limitations. YouTube formats
that require a JavaScript runtime may be unavailable. Multi-video posts currently
preview and download their first item. Available containers and quality come from
the source; arbitrary format conversion is not implemented.

## Fixes covered by regression tests

- Single-URL extractors no longer produce an empty format list.
- Preview and download both select the first item in multi-video posts.
- Unknown codecs are kept distinct from explicitly missing audio/video.
- Each quality retains distinct available output containers; audio formats are selectable too.
- Unknown embedded audio is paired with a known separate audio stream when one exists.
- Heightless social videos preserve a usable option and their original container.
- Share text pasted into the input resolves its URL; query punctuation remains editable.
- Queue failures release the loading state and allow retry; duplicate taps are guarded.
- Audio choices use Music storage, and merges explicitly select their intended tracks.
- Cancellation interrupts blocking download work; publication rolls back unrecorded files.
- WorkManager declares its data-sync foreground service type for Android 14.
- Notification delivery checks permission and tolerates revocation.
- Direct endpoint filenames cannot override a recognized media container with `.php`.
- Direct image previews use the image URL itself.
