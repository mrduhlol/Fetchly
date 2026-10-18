# Fetchly rebuild — design

- Date: 2026-10-17
- Status: built and released — v2.0 tagged after a clean CI run (139 tests, 0 failures); the release carries `fetchly_v2.0.apk` (123.7 MB)
- Supersedes: the entire current application (v1.8 and earlier, plus the uncommitted partial migration sitting in the working tree)

## Intent and why

The app installed on the owner's phone fetches links through a Fetchly API
server. That server no longer exists, so every fetch fails with "Check your
internet connection and try again", and Settings still shows an API URL
field. The owner has asked for this, plainly: **rebuild the app from
scratch — paste a link, fetch it with yt-dlp on the device, no API
anywhere, new GUI.**

The rebuild is not starting from zero knowledge. The repo already contains
a partial migration (v1.8 removed the API field; uncommitted work routes
social links through an on-device engine) and its lessons are kept as
requirements below. What gets thrown away is the code, not the lessons.

## What success looks like

1. Paste any supported link (YouTube, social, or a direct media file) and
   tap Fetch. On-device yt-dlp resolves it: title, thumbnail, duration,
   and a list of quality options. No server, no account, no API key, no
   configuration screen for any of it.
2. Pick a quality, tap Download. A single playable file lands in
   `Movies/Fetchly/`, `Music/Fetchly/`, or `Pictures/Fetchly/` —
   **with sound**, merged on the device when the source only provides
   separate video and audio streams.
3. Downloads run in the background with progress, notifications, cancel
   and retry; retry survives expired links by re-resolving.
4. Settings contains: theme, default quality, clear history, about.
   Nothing else. Nothing resembling an API.
5. The owner installs the release APK from GitHub and a 1080p YouTube
   download plays correctly with audio.

## Decisions the owner made

- **Build from scratch.** New source tree; the old application code is
  removed (the repo has done this once before, commit `4e7517a`
  "chore: remove everything for fresh rebuild").
- **yt-dlp on the device is the only fetcher** for YouTube, social, and
  generic links. No API backend of any kind, ever.
- **New GUI** — the screens are re-designed, not carried over.
- **Commit and push after every file change** while building.

## Technical facts established before writing this (all verified today)

These are the load-bearing facts; they were tested on the build machine,
not assumed:

1. **yt-dlp now works for YouTube.** Version 2026.08.19 (latest stable)
   extracts title/metadata/formats cleanly. The repo currently pins
   `yt-dlp==2024.08.06` (two years old) — that pin is why YouTube was
   fragile and must be updated.
2. **YouTube no longer serves video-with-audio as a single file.** For a
   test video: 48 formats, *zero* of them muxed; all either video-only or
   audio-only. Anything above ~240–360p requires downloading a video
   stream and an audio stream and merging them.
3. **Merging works with a `-c copy` mux.** Verified end-to-end: the
   1080p video stream (H.264, 81 MB) and the audio stream (AAC m4a,
   3.4 MB) of a test video, merged with
   `ffmpeg -i video -i audio -c copy -movflags +faststart out.mp4` →
   valid 1080p H.264 + AAC MP4, correct duration. No re-encode.
4. **A plain HTTP client cannot download these stream URLs.** Raw curls
   of freshly extracted URLs were throttled to ~12 KB/s or cut off
   mid-range (a 206 response truncated at ~47 KB). The *same* formats
   downloaded through yt-dlp's own downloader sustained ~4.5 MB/s
   (81 MB in 20 s), including YouTube's periodic crawl-burst throttling —
   yt-dlp rides through the slow phases with range-chunk retries.
   **Decision: yt-dlp does the downloading on the device; never scrape
   extracted URLs into OkHttp.**
5. **No JavaScript runtime is needed on the device (for now).** yt-dlp
   2026 defaults `js_runtimes` to `deno`, but with JS runtimes fully
   disabled (the phone condition — no deno/node/quickjs) extraction
   returned the identical 48 formats, and the fast downloads from fact 4
   were reproduced with `--no-js-runtimes`. yt-dlp does print a
   "YouTube extraction without a JS runtime has been deprecated" warning:
   accepted risk for v2.0, with a documented revisit path (bundle
   QuickJS + EJS challenge-solver scripts) if formats or speeds degrade.
6. **An ffmpeg for Android exists and is maintained.** `FFmpegKit` was
   retired in 2025, but `dev.ffmpegkit-maintained:ffmpeg-kit-*` on Maven
   Central is the actively maintained drop-in continuation (LGPL-3.0,
   SDK 35, 16 KB page support, arm64-v8a + x86_64). Because it is a
   *library*, not an executable, yt-dlp cannot shell out to it: the
   orchestration is "yt-dlp downloads the streams to local files →
   FFmpegKit merges the local files".
7. **Direct media files** (plain mp4/mp3/jpg URLs) still resolve with a
   fast HEAD/GET probe and download as a single stream — no yt-dlp, no
   merge.

## Architecture

New app, same repo, package `com.fetchly.app` (debug builds keep the
`.debug` suffix), Kotlin + Jetpack Compose + Material 3.

```
app/src/main/
  java/com/fetchly/app/
    FetchlyApp.kt            application: graph init, temp cleanup
    MainActivity.kt          single activity, Compose
    navigation/              routes: home → result → downloads → settings/about
    ui/theme/                colors, type, theme (system/light/dark)
    ui/home/                 HomeScreen + HomeViewModel   (paste + fetch)
    ui/result/               ResultScreen                 (media info + qualities)
    ui/downloads/            DownloadsScreen + ViewModel  (active + history)
    ui/settings/             SettingsScreen + ViewModel   (theme, quality, history)
    ui/components/           UrlInput, FetchButton, QualityCard, ProgressRow…
    domain/model/            Platform, MediaType, MediaInfo, QualityOption,
                             StreamRef, HistoryEntry, DownloadStatus
    domain/resolve/          ResolveUrlUseCase
    domain/security/         UrlSecurity (scheme/host/size guards)
    domain/util/             MediaSniffer, MimeTypes, RelativeTime, FormatSize
    data/engine/             YtDlpEngine, YtDlpMapper   (Chaquopy ↔ Python)
    data/direct/             DirectMediaResolver       (HEAD probe for direct files)
    data/download/           DownloadWorker, DownloadQueue, MediaStorePublisher,
                             DownloadNotifications, TempFiles
    data/db/                 FetchlyDatabase, HistoryDao, HistoryRepository (Room)
    data/prefs/              SettingsStore (DataStore)
    data/AppGraph.kt         manual DI (no Hilt — keep it lean)
  python/fetchly_yt.py       yt-dlp bridge: extract() metadata JSON;
                             download() streams with progress + cancel
```

### Data shapes (the important ones)

```kotlin
data class StreamRef(          // one downloadable HTTP stream
    val url: String, val ext: String, val vcodec: String, val acodec: String,
    val height: Int?, val abr: Int?, val sizeBytes: Long?, val formatId: String,
)

data class QualityOption(      // one line in the quality list
    val id: String,
    val label: String,         // "1080p", "720p", "Audio"
    val kind: Kind,            // VIDEO (streams merged), AUDIO, FILE (single direct file)
    val container: String,     // "mp4" | "mkv" | "m4a" | "webm" | …
    val streams: List<StreamRef>,
    val needsMerge: Boolean,   // streams.size > 1
    val sizeBytes: Long?,      // sum when known; null otherwise, never invented
)
```

`HistoryEntry` keeps what a retry needs: source URL + the chosen option's
stream signature (ext/vcodec/acodec/height/abr/formatId) + the last known
stream URLs. Retry strategy: if stored stream URLs fail or 403/410, re-run
resolution on `sourceUrl`, match the option by signature, and use fresh
URLs. If re-resolution can't match it, say so honestly.

### The pipeline (fetch → download)

**Resolve (Fetch tap):** validate URL (http/https, public host rules like
the current `UrlSecurity`) → platform detect → direct file? probe; else →
`YtDlpEngine.extract(url)` in Chaquopy (lazy Python start, off main
thread) → `YtDlpMapper` builds `MediaInfo` + `QualityOption`s:

- Video options: group video-only streams by height (2160/1440/1080/720/
  480/360/240), pick the best stream per height by letter-tier:
  prefer `avc1` > `vp9`/`vp09` > `av01` for compatibility, and pair each
  with the best audio stream (`mp4a`/`aac` preferred, else `opus`).
  Container: `mp4` when (avc1 + aac), else `mkv` (plays everywhere;
  honest, not a fake mp4).
- Audio option: best `m4a` when available, else best `opus/webm`.
- Sizes: sum of the streams' known sizes; null if unknown.
- A link that yields exactly one direct-stream format degrades to a
  `FILE` option and skips the merge machinery.

**Download (tap Download):** enqueue a `DownloadWorker` (foreground,
`NetworkType.CONNECTED`, backoff, tag `fetchly_downloads`). The worker
drives **yt-dlp in the Python engine for every extracted stream** — never
a naive GET of an extracted URL (facts 4 and 5):

1. Stage `downloading video` → `fetchly_yt.download(url, format_id,
   dest_dir, hook)` lets yt-dlp transfer the stream into a `cacheDir`
   temp file. The progress hook (a Kotlin object called from Python via
   Chaquopy) reports downloaded bytes, total (or estimate), and speed
   into WorkManager progress and the notification; indeterminate when the
   total is unknown. Raising from the hook aborts the transfer — that is
   the cancel path.
2. Stage `downloading audio` → same for the audio stream when the option
   has one. Single-stream options (audio-only, direct-file, a progressive
   format if a site only offers those) skip straight to stage 3.
3. Stage `merging` → FFmpegKit `execute("-i v -i a -c copy -movflags
   +faststart out.mp4")` (`.mkv`, no faststart). Indeterminate progress —
   no fake percentages. A single stream is still remuxed (`-c copy`)
   into a proper container extension (`.m4s` → `.mp4`/`.m4a`).
4. Verify before claiming completion: file exists, nonzero, magic bytes
   match the container (`MediaSniffer`), reported length matches when
   known. Temp files deleted on failure/cancel.
5. Publish to MediaStore (`Movies/Fetchly`, `Music/Fetchly`), update the
   history entry, show the completion notification with a tap-to-open.

Cancel stops the worker and cleans temps. No pause/resume (unchanged
limitation, stated in About/release notes).

### Design-review outcomes (2026-10-17)

A fresh reviewer read this document before the owner did. Its open
questions and the answers now baked in:

- **JS runtime for YouTube (the reviewer's biggest question):** not
  bundled in v2.0. Fact 5 shows extraction *and* full-speed downloads
  with JS runtimes disabled. If YouTube later degrades, the revisit is a
  defined workstream: ship QuickJS (small native binary in `jniLibs`)
  plus EJS challenge-solver scripts, and enable `js_runtimes` /
  `remote_components` in the extractor params. Out of scope until
  evidence demands it.
- **HLS / m3u8-only sources:** supported by the same pipeline — yt-dlp's
  own downloader handles HLS natively, no custom segment logic. The
  mapper is protocol-agnostic: whatever streams yt-dlp returns become
  options.
- **Per-video walls (PO tokens, SABR):** accepted risk. The worker
  auto-re-resolves once on 403/410-style failures, then surfaces an
  honest error naming the cause. No yt-dlp-metadata + custom-download
  hybrid. During implementation, spot-check several varied videos
  (music video, Shorts, long video) to gauge the failure rate; if a
  meaningful share of real links fails, the JS-runtime workstream
  becomes the contingency plan.
- **"Social" scope:** the success contract is YouTube (watch links,
  youtu.be, Shorts, music) plus direct media files. Instagram/TikTok/etc.
  are best-effort through yt-dlp's extractors; when one needs cookies or
  login, the app says exactly that — honest, not broken. README and
  release notes state this plainly.
- **Progressive/muxed formats**, when a site still returns them: become
  normal single-stream options (`needsMerge = false`), no special casing.

### Screens (wireframes — the real GUI is Compose/Material 3)

```
HOME                        RESULT                      DOWNLOADS
┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ Fetchly       ↓  ⚙   │    │ ←  Choose quality    │    │ ←  Downloads         │
│                      │    │ ┌─────┐ Title        │    │  Downloading         │
│  Paste. Choose.      │    │ │thumb│ channel      │    │  Title…              │
│  Fetch.              │    │ └─────┘ 3:33         │    │  62% • 4.2 MB/s      │
│ ┌──────────────────┐ │    │                      │    │  ▓▓▓▓▓▓░░░░   merging│
│ │ https://youtu…   │ │    │ Quality              │    │                      │
│ └──────────────────┘ │    │ (•) 1080p • ~84 MB   │    │  History             │
│       [ Paste ]      │    │ ( ) 720p  • ~36 MB   │    │  ✔ Title — 1080p MP4 │
│                      │    │ ( ) 360p  • ~18 MB   │    │  ✕ Title — failed    │
│    ═══ FETCH ═══     │    │ ( ) Audio • ~3 MB    │    │     [ Retry ]        │
│  Detected: YouTube   │    │    ═ DOWNLOAD ═      │    │                      │
└──────────────────────┘    └──────────────────────┘    └──────────────────────┘

SETTINGS
┌──────────────────────┐
│ ←  Settings          │
│ Appearance           │
│  Theme      [System▾]│
│ Downloads            │
│  Quality    [Ask  ▾] │
│ History              │
│  Clear history       │
│ About                │
│  Fetchly 2.0         │
└──────────────────────┘
```

- Home: clipboard suggestion appears on resume when the clipboard holds a
  link; share-intent from other apps prefills the field. Fetch shows a
  staged indeterminate status ("Resolving link…" / "Asking yt-dlp…").
- Result: quality list only shows what the extractor actually returned —
  best first; sizes only when known.
- Downloads: active items with progress/speed/stage; history below with
  status; tap opens a details sheet (open / share / delete file / retry).
- Settings: theme, default quality (Ask / Best), clear history, about.
  **No URL/API/endpoint field of any kind** (hard rule).

### Build, versions, CI

- `yt-dlp==2026.08.19` (re-check for a newer stable at build time).
- Chaquopy: keep 15.0.1 / Python 3.10 unless verification shows problems
  (16 KB page alignment on the Python libs is a known risk to check;
  upgrade to the newest Chaquopy that supports it if needed).
- `dev.ffmpegkit-maintained:ffmpeg-kit-full` (LGPL-3.0) — full tier
  chosen for certainty over size; ≈+20–30 MB per-ABI APK size is
  accepted for a sideloaded personal app.
- ABIs: `arm64-v8a` + `x86_64` only. armeabi-v7a is dropped: ffmpeg-kit
  publishes no 32-bit builds, and without a merge engine 32-bit devices
  could not download any video at all. (Owner's call to reverse, but
  modern phones are all 64-bit.)
- compileSdk 35 (needed by the ffmpeg AAR); AGP/Gradle bumped to match;
  minSdk 29; targetSdk 34; applicationId `com.fetchly.app`
  (`applicationIdSuffix ".debug"` for debug, as today).
- CI (`release-apk.yml`): update platform to android-35/build-tools 35,
  keep "build debug APK + unit tests + attach to release" flow. Tag
  `v2.0` (versionCode 20) produces the release. The APK is debug-key
  signed, as every previous release: **uninstall the old app first**
  (signature differs); say this in the release notes.

### Verification plan

- Unit tests: mapper (stream pairing, container choice, size sums,
  degraded single-stream/file cases), platform detection, URL security,
  media sniffer, mime/size formatting, retry signature matching.
- Local: `./gradlew testDebugUnitTest assembleDebug` on the build machine
  (JDK 17 + Android SDK 35 + a host Python ≤ 3.12 for Chaquopy's build pip —
  verified 2026-10-18; Python 3.13+ removed `cgi`, which Chaquopy 15's
  bundled pip imports).
- CI: tag build green, APK attached to the GitHub release.
- Owner (device): paste a YouTube link → fetch → 1080p download → play
  with sound; a Shorts link and a second, different video (varied
  formats); audio-only download; a direct mp4 link; a failing link
  (private video → honest error); cancel + retry; an Instagram
  public post (best-effort — an honest, clearly-worded failure is
  acceptable).

## Non-goals

- Playlists/channels: first playable entry only (as today).
- Private, login-walled, or DRM content; live streams.
- Pause/resume; resumable partial downloads.
- Play Store: GitHub releases + sideload only.
- Subtitle embedding, re-encoding, format conversion beyond `-c copy`.

## Anti-goals (would make this a failure)

- Any backend, API key, account, or remote configuration — even as a
  fallback.
- Silent videos passed off as successes, fake progress, invented sizes.
- Failures that say "network error" when the truth is "this source isn't
  supported" (or vice versa). Errors must name the real cause in plain
  words.

## Constraints

- The build machine cannot run the app (no device/emulator): correctness
  is proven by unit tests + build + the owner's device test. Test builds
  should be quick to produce (debug APK via CI artifact or local build).
- Commit + push after every file change (owner's workflow requirement).
- The old app's useful lessons are requirements: verify before claiming
  completion; MediaStore publishing without broad permissions; honest
  staging text; cancellation-safe temp handling.

## Left to the builder (me, in the plan)

- Exact Compose/M3 implementation and insets handling per screen.
- The Python engine's exact API (extract / download entry points, error
  JSON shape) and the progress-hook → Kotlin bridge details (Chaquopy
  callback object, event throttling, cancel semantics).
- Merge failure policy in detail (retain partials? always delete?).
- Room schema/migration (fresh DB; history fields as above).
- Notification channel details; details-sheet actions; share intent
  filters (http/https only).
- CI workflow edits; README/CHANGELOG/docs rewrite for v2.0; release
  notes; version bump. The stray git tag named `main` is ignored (pushes
  use explicit refspecs).
- Order of work and per-step verification.
