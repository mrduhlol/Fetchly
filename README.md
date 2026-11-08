<div align="center">

# Fetchly

**Paste. Choose. Fetch.**

Paste a public media link, pick a quality, save it to your device.

<p><img src="app/src/main/res/drawable-nodpi/fetchly_logo.png" width="280" alt="Fetchly Apollo app icon" /></p>

<p><a href="https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly.apk"><img src="docs/download-apk.svg" width="220" alt="Download APK for Android" /></a></p>

<sub>v2.4 · Android 10 (API 29)+</sub>

Upgrading from the old 2.0 APK requires uninstalling it first because its signing key differs. This clears app settings and in-app history. Updates from the published 2.1 APK keep its signing key and install in place. [Release notes](docs/release-notes/v2.4.md) cover this update.

</div>

## What it does

Paste a link — YouTube, another public site, or a direct media file. Fetchly resolves it **on your phone** with a bundled yt-dlp engine, shows the real formats, then downloads and merges the quality you pick into `Movies/Fetchly/`, `Music/Fetchly/` or `Pictures/Fetchly/`.

No backend, no account, no API key, no configuration. Resolution, downloading and muxing all run on-device.

## Features

- **Personal appearance** — gold and white palettes, deferred launcher icon updates, and 80%, 100%, or 120% app size.
- **Media previews and MP3** — large landscape/portrait previews, video container buttons, and MP3 bitrate choices from 96 to 320 kb/s.

- **Share to fetch** — send a public link from another app and get a compact format/quality dialog over the source app.
- **X bulk ZIP** — bundle available photo and video attachments from a multi-media X post into one ZIP.
- **Pause and resume** — freeze a download in place with its byte count and resume from retained partial data when the source supports it.
- **Batch review** — check up to 50 links together, set format and filename per item, and queue two downloads at a time.
- **Real quality choices** — container, reported size, sound availability and source language. Silent video needs confirmation and is never selected as best by default.
- **Resume and retry** — eligible direct files resume from validated checkpoints; failed segments retry without re-downloading completed bytes.
- **Honest progress** — transfer, merging, checking and saving are distinct stages, with an **Open file** action when done.
- **Actionable errors** — sign-in, private or deleted media, geo-block, rate limit, expired URL, full disk and merge failure each name their real cause.
- **Duplicate-aware history** — search, media filters, and reuse of active or saved copies instead of downloading the same file twice.
- **Updateable engine** — Settings → Download engine checks for verified, compatible official yt-dlp updates.
- **Works with** — YouTube video, Shorts and audio; direct mp4/mp3/jpg/etc.; and best-effort Instagram, TikTok, X, Reddit, Facebook, Pinterest and LinkedIn. Public media only.

## Documentation

### Test results

| Check | Recorded result |
| --- | --- |
| Kotlin unit tests | **252 passing** |
| Python bridge tests | **28 passing** |
| Android lint | Passes with warnings |
| Debug APK | Assembles successfully |

The suites cover URL validation, extraction mapping, quality pairing, queue behavior, cancellation, storage, resume/retry, the share dialog and error classification. Live and fixture extraction checks covered YouTube, Instagram, LinkedIn, Pinterest and X; Reddit and TikTok restrictions were identified rather than hidden. Android 14 emulator checks resolved, downloaded and opened a public WebP.

The full scope, including what is not device-verified, is in [`docs/VERIFICATION.md`](docs/VERIFICATION.md).

### Performance

Version 2.2 warms the extraction runtime off the UI thread, caches the Python module, and shares overlapping requests for the same URL. Live latency gains have not been benchmarked.

Fetchly fetches direct files with up to four parallel byte ranges when the source supports ranges, has a strong ETag and a known length. YouTube video and audio streams download concurrently and are muxed on-device.

Controlled 8 MiB benchmark, with each response limited to 256 KiB per 100 ms:

| Path | Time |
| --- | --- |
| Single stream | 3,121 ms |
| Four parallel ranges | 875 ms |
| Result | **3.57× throughput** (72% less time) |

An earlier run measured 3,165 ms vs 884 ms (**3.58×**). Both runs produced output identical to the fixture. This is a local JVM test of the range path against Fetchly's own single-stream path — not a phone, public-internet or competitor benchmark.

The GitHub research compares download techniques, not measured throughput:

| Project | Relevant approach |
| --- | --- |
| aria2 | Segmented HTTP, mirrors, resume and integrity checks |
| Gopeed | Multi-connection HTTP and concurrent tasks; also P2P |
| Motrix | Desktop download manager, up to 64 threads per task |
| XDM | Resumable transfers and browser media capture |
| yt-dlp | Media extraction; HLS/DASH fragments default to concurrency 1 (Fetchly uses 4) |
| Seal / YTDLnis | Android media downloaders built on yt-dlp |

None of these projects has a verified head-to-head speed result against Fetchly in this repo. Star counts and connection counts do not establish throughput; a fair ranking needs the same source, file, network, device, output quality and completion rule. The only measured speedup claimed here is Fetchly's four-range path versus its own single-stream path.

The full benchmark protocol and GitHub survey are in [`docs/DOWNLOAD_PERFORMANCE.md`](docs/DOWNLOAD_PERFORMANCE.md).

### More docs

- [`docs/RESOLUTION.md`](docs/RESOLUTION.md) — how a link becomes a download
- [`docs/RELIABILITY.md`](docs/RELIABILITY.md) — resume, retries, failures and batch behavior
- [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) — permissions used and why
- [`docs/STORAGE.md`](docs/STORAGE.md) — where downloads and history live
- [`docs/SECURITY.md`](docs/SECURITY.md) — security model
- [`docs/RELEASE.md`](docs/RELEASE.md) — releases and limitations
