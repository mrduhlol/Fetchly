# Fetchly rebuild — design

- Date: 2026-10-17
- Status: draft — awaiting owner approval
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
3. **Merging works with a `-c copy` mux.** Verified end-to-end: format 137
   (1080p H.264, 81 MB) + format 140 (AAC m4a, 3.4 MB) downloaded with a
   plain HTTP GET (custom User-Agent, no cookies) and merged with
   `ffmpeg -i video -i audio -c copy -movflags +faststart out.mp4` →
   valid 1080p H.264 + AAC MP4, correct duration. No re-encode.
4. **The stream URLs download with a plain HTTP client.** No special
   headers, no cookies; 200/206 responses with the app's User-Agent.
   They are signed and expire (hours, not days) — retry flows must account
   for that.
5. **An ffmpeg for Android exists and is maintained.** `FFmpegKit` was
   retired in 2025, but `dev.ffmpegkit-maintained:ffmpeg-kit-*` on Maven
   Central is the actively maintained drop-in continuation (LGPL-3.0,
   SDK 35, 16 KB page support, arm64-v8a + x86_64). Because it is a
   *library*, not a binary, yt-dlp cannot shell out to it: the app stays
   "yt-dlp for metadata, Kotlin for downloads, FFmpegKit for merging".
6. **Direct media files** (plain mp4/mp3/jpg URLs) still resolve with a
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
  python/fetchly_yt.py       yt-dlp extract(): JSON out, app fields only
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
`NetworkType.CONNECTED`, backoff, tag `fetchly_downloads`):

1. Stage `downloading video` → GET stream 1 into `cacheDir` temp file
   (OkHttp, `User-Agent: Fetchly/2.0`, progress bytes/total + speed;
   indeterminate when size unknown).
2. Stage `downloading audio` → same for stream 2, when present.
3. Stage `merging` → FFmpegKit `execute("-i v -i a -c copy -movflags
   +faststart out.mp4")` (or `.mkv`, no faststart). Indeterminate
   progress — no fake percentages. A single stream is still remuxed
   (`-c copy`) into a proper container extension (`.m4s` → `.mp4`/`.m4a`).
4. Verify before claiming completion: file exists, nonzero, magic bytes
   match the container (`MediaSniffer`), reported length matches when
   known. Temp files deleted on failure/cancel.
5. Publish to MediaStore (`Movies/Fetchly`, `Music/Fetchly`), update the
   history entry, show the completion notification with a tap-to-open.

Cancel stops the worker and cleans temps. No pause/resume (unchanged
limitation, stated in About/release notes).

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
  (JDK 17 + Android SDK installed for this work).
- CI: tag build green, APK attached to the GitHub release.
- Owner (device): paste a YouTube link → fetch → 1080p download → play
  with sound; audio-only download; a direct mp4 link; a failing link
  (private video → honest error); cancel + retry.

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
- Merge failure policy in detail (retain partials? always delete?).
- Room schema/migration (fresh DB; history fields as above).
- Notification channel details; details-sheet actions; share intent
  filters (http/https only).
- CI workflow edits; README/CHANGELOG/docs rewrite for v2.0; release
  notes; version bump; the stray git tag `main` is ignored (push uses
  explicit refspecs).
- Order of work and per-step verification.
