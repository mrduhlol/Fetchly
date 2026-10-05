# Changelog

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
