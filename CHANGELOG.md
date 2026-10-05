# Changelog

## Fetchly 1.2

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
