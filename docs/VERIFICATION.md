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

## Direct-download resume and retries — 2026-11-06

Local verification: **189 Kotlin tests, 12 Python tests, Android lint and debug
APK assembly passed**. The Kotlin direct-downloader tests now cover cancellation
followed by resume in a new instance, retrying a disconnected segment from its
saved offset, skipping completed segments, changed ETags and URLs, malformed
checkpoints, cache eviction, bounded retry budgets, cancellation during backoff,
small-file resume, concurrent replacement attempts and local storage failures.
Deletion cleanup is tested against unrelated jobs' files.

Resume uses the existing Retry action. It requires a range-capable source with a
strong ETag and known size; unsupported or changed sources restart safely. The
private cache and sidecar survive interruptions, but Android cache eviction and
the 48-hour stale sweep can remove them. These are JVM integration tests using
MockWebServer, not Android process-kill or real-device lifecycle tests.

## Source recovery and completion reliability — 2026-11-07

**206 Kotlin tests, 21 Python tests, Android lint and APK assembly passed.**
The Python suite was run against the same yt-dlp 2026.10.26 and packaging 25.0
requirements used in the APK. Python 3.12 is now bundled. The official-wheel
smoke test fetched the real compatible wheel from PyPI, verified its hash and
staged it successfully in a temporary directory.

New regression coverage includes bot-check versus expired-URL classification,
source restrictions and engine recovery advice, indeterminate non-transfer
stages, guarded retry actions and command failures, stale terminal workers,
analysis cancellation, audible-quality defaults, source language selection,
refresh preservation of language/bitrate, estimated-size metadata, update hashes,
unsafe archives, HTTPS downgrade rejection, runtime compatibility, failed-update
fallback and newer-bundle precedence.

Android 14 emulator functional check: a public WebP image at
`https://www.gstatic.com/webp/gallery/1.webp` was resolved, explicitly selected,
downloaded and published. The completed row displayed `Saved — 29.6 KB` and
`Open file`. The final APK also passed lint/build after the remaining copy and
update-integrity changes.

Device support subsequently failed with `Could not communicate with device
support. Try refreshing devices.` Refreshing failed too. Device work was stopped;
file opening, the in-app live engine-update check, and final screenshots remain
unverified on Android. No screenshot or native visual pass is claimed for those
changed screens. JVM tests do not substitute for OEM battery-policy testing.

See [RELIABILITY.md](RELIABILITY.md) for behavior and remaining source limitations.

### Batch and library work (2026-11-07)

Added regression coverage for batch parsing and public-link limits, audible
quality caps, per-file failures, cancellation with a late extractor result,
partial queue retries, duplicate-tap protection, custom filenames, queue
admission order/limits, refreshed stream URLs, source/language distinctions,
search filters, and Unicode filename byte limits. The Kotlin suite now has
218 passing tests; the Python suite has 21 passing tests.

Device validation limitation: fetchly_test (Android 14, emulator-5556) opened
Fetchly and returned a Home accessibility snapshot. Device screenshot capture
then failed twice with "The device hub could not complete the request" after
checking the reported device state as advised. Further device work was stopped.
The new batch/library UI and real batch transfer have not been visually or
interactively verified on this emulator in this pass.

Final validation: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
completed successfully offline with Kotlin/KSP incremental compilation disabled.
The final APK is `app/build/outputs/apk/debug/app-debug.apk`. Lint has no errors;
existing warnings remain. `git diff --check` also passed. No release was published.
