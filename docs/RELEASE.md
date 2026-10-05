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

## Known limitations (V1)

1. Platform adapters in `backend/` are honest stubs: without a permitted
   extraction mechanism configured, analysis returns
   "This source isn't currently supported." No formats are ever faked.
2. Genuinely implemented client-side: URL validation, platform detection,
   API integration, quality selection, WorkManager downloads to MediaStore,
   notifications, history, share-intent receive, settings, themes.
3. Re-delivery of a share intent while the app is already open applies on
   next launch (documented simplification in `MainActivity.onNewIntent`).
4. No account system, no cloud sync — by design.
