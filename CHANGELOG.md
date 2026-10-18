# Changelog

## Fetchly 2.0

A from-scratch rebuild. v1.8's bundled engine went stale and YouTube began
refusing it ("Please sign in"), which surfaced as a generic *"Fetchly
couldn't retrieve media information from this link."* v2.0 ships a current
on-device engine and names the real cause of every failure.

### Fixed

- YouTube resolution works again: the bundled yt-dlp engine is current
  (2026.08.19), packaged with Chaquopy and running on-device
- Failures now name the real cause — sign-in demand, private or deleted
  video, geo-block, rate limit, expired download link, full disk, merge
  failure — instead of one generic sentence; the raw engine line is kept
  alongside the friendly wording

### New

- Video + audio are fetched as separate streams and muxed on-device with
  ffmpeg-kit; direct media files (mp4/mp3/jpg/…) download as-is with
  `Accept-Encoding: identity` so the byte count matches Content-Length
- Downloads screen: live stage, percent and speed, cancel; history below
  with retry and a details sheet (open, share, delete — deleting removes
  the published file)
- Result downloads verify themselves before reporting success: magic-byte
  check, non-empty file, and ≥95% of the expected size when known
- Expired stream URLs (403/410) transparently re-resolve once before the
  download is declared failed
- Settings is theme, default quality and history — and nothing else.
  Fetchly has no server, no account and nothing remote to configure
- About screen shows the live bundled engine version, so a stale engine is
  visible on-screen instead of guessed at

### Quality

- 128 unit tests, including regression guards: honest error tables, no
  invented sizes, and "settings contains nothing that points at a server"

## Fetchly 1.8

- YouTube via embedded on-device yt-dlp: paste a link, get thumbnail,
  title, duration, and real quality options
- Removed every backend/API remnant: no server, no API URL, no backend
  docs, no backend wording anywhere in code or UI

## Unreleased — YouTube via on-device engine

- Embedded yt-dlp (Chaquopy Python runtime) — analysis runs on-device,
  still no backend
- YouTube links show thumbnail, title, duration, uploader, and real
  quality/format options from the extractor
- Only genuinely downloadable streams are offered (storyboards and
  URL-less formats filtered out)
- Engine errors classified honestly (private, unavailable, unsupported)

## Unreleased — local-first

- Removed the backend dependency entirely: analysis runs on-device,
  no server to deploy or configure, no API URL anywhere
- Direct media links (video, audio, images) resolve locally with real
  content type, size, and filename
- Social platforms honestly report "This source isn't supported yet."
- Capability matrix gates every claim of support
- Error messages classify the actual problem (unsupported, invalid,
  unreachable, unavailable)

## Fetchly 1.7

Stabilization release for the V1 line. No new product features.

### Improved

- Removed dead code (unused progress key, unused ViewModel reset)
- HTTP request logging is now debug-build only
- Manifest, permission, and lifecycle audit: no issues found
- Hidden diagnostics screen kept (5-tap unlock, no secrets shown)

## Fetchly 1.6

### Improved

- Smoother progress: identical download updates are deduplicated before
  reaching the UI
- Primary button now has a visible disabled state (state is never
  color-only)
- History group headers are screen-reader headings
- Security audit: no new issues — validation, sanitization, scoped storage,
  and rate limiting unchanged and documented

## Fetchly 1.5

### Improved

- Downloads are verified as real media via magic bytes — HTML/JSON error
  pages are rejected instead of saved
- Details screen falls back to the recorded size when MediaStore is silent
- Deep Android integration pass: share re-delivery, content URIs with real
  MIME types, MediaStore locations, no broad storage permissions

## Fetchly 1.4

### Improved

- Downloads wait for connectivity and back off exponentially between retries
- Retry re-checks storage first and reports honest outcomes
- Retry problems surface in the UI and in notifications

## Fetchly 1.3

### Improved

- Richer media preview: duration badge, type-specific facts (dimensions,
  bitrate, audio presence) shown only when the source provides them
- Format rows surface bitrate and dimensions when known
- Staged analyzing messages without fake progress percentages
- Normalized file extensions (e.g. `jpeg` saved as `.jpg`)

### Fixed

- Extension handling for containers whose suffix differs from the format name

### Improved

- More reliable media fetching
- Better download handling with an explicit queue
- Background downloads that survive leaving the app
- Download notifications with Cancel, Retry, and Open actions
- Better error handling (no raw server errors shown)
- Android share integration with re-delivery while open
- Clipboard paste suggestion on resume
- Download history grouped by day with details, retry, and safe delete
- Duplicate download detection
- Low-storage and connection-loss handling

### Fixed

- Network/API error handling
- Download state persistence via Room
- Storage handling and temp-file cleanup
- Failed download reporting with retry
- Unit-testable URL validation (no Android dependencies in domain logic)
