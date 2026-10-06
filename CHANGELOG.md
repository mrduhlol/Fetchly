# Changelog

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
