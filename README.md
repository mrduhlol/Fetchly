# Fetchly — Paste. Choose. Fetch.

Native Android app (Kotlin + Jetpack Compose + Material 3) for inspecting publicly
accessible media URLs, choosing an available quality/format, and saving media to
the device where downloading is permitted.

## Scope

- Only supports content that is publicly accessible and permitted to be downloaded.
- No private-account downloading, no login/session scraping, no DRM bypass,
  no paywall bypass, no circumvention of platform protections.
- If a source cannot be supported through a permitted mechanism, the app reports:
  "This source isn't currently supported."

## Project layout

Standard single-module native Android app (`app/`) using clean architecture:

- `presentation/` (Compose UI + ViewModels)
- `domain/` (models, repositories, use cases)
- `data/` (remote API, local Room, repositories, download)

## Backend

The app talks to a Fetchly API for platform analysis:

- `POST /api/analyze` with `{ "url": "..." }`
- Configure the base URL via `BuildConfig.FETCHLY_API_BASE_URL`
  or the in-app Settings screen (debug override).

See `docs/API.md` and `backend/` for the API contract and a reference adapter server.

## Build

Requirements: JDK 17, Android SDK (API 29+ target, compileSdk 34).

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```

Downloads are stored via MediaStore (`Movies/Fetchly/`, `Music/Fetchly/`,
`Pictures/Fetchly/`) on Android 10+.

## Status

V1 in progress. See `docs/` for permissions, storage, security, and release notes.
