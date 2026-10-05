# Release / build

## Requirements

- JDK 17, Android SDK with compileSdk 34, minSdk 29 (Android 10+).

## Environment variables

| Variable | Purpose |
|---|---|
| `FETCHLY_API_BASE_URL` | Base URL of the Fetchly API (e.g. `https://api.example.com/`). Baked into `BuildConfig` at build time. Can also be set via `local.defaults.properties` or the in-app Settings override. |

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (needs signing config)
./gradlew bundleRelease        # release AAB for Play Store
./gradlew testDebugUnitTest    # unit tests
```

## Known limitations (V1.2)

1. Platform adapters in `backend/` are honest stubs: without a permitted
   extraction mechanism configured, analysis returns
   "This source isn't currently supported." No formats are ever faked.
2. No pause/resume: WorkManager one-shot downloads genuinely don't support
   it, so no pause UI is shown rather than a fake one. Cancel + retry are
   fully supported.
3. Retry reuses the stored download URL. If the backend issues short-lived
   URLs that expire, a failed retry needs a fresh analyze (paste the link
   again) — the app says so instead of looping forever.
4. Share-intent re-delivery while already open updates Home immediately.
5. Duplicate detection keys on normalized source URL + backend format id.
6. Room schema v2 uses destructive migration on upgrade (no shipped user
   base with data to preserve yet).
7. No account system, no cloud sync — by design.
