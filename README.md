<div align="center">

<img src="app/src/main/res/drawable-nodpi/fetchly_logo.png" width="120" alt="Fetchly logo" />

# Fetchly

**Paste. Choose. Fetch.**

![version](https://img.shields.io/github/v/release/mrduhlol/Fetchly) — Latest: **v1.7**

Paste a public media link, pick a quality, save it to your device.

[![Download APK](https://img.shields.io/badge/Download-APK-3B82F6?style=for-the-badge&logo=android)](https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly_v1.7.apk)

</div>

## What it does

1. Paste a direct media link (mp4, mp3, jpg, …)
2. Fetchly verifies it on-device and shows what's actually there
3. Download straight to `Movies/Fetchly/`, `Music/Fetchly/` or `Pictures/Fetchly/`

Only publicly accessible media where downloading is permitted. Anything else gets: *"This source isn't supported yet."*

No backend, no accounts, no configuration — everything runs on your device.

## Build it yourself

Needs JDK 17 + Android SDK (compileSdk 34).

```bash
./gradlew assembleDebug      # APK
./gradlew bundleRelease      # Play Store AAB
./gradlew testDebugUnitTest  # tests
```

## Docs

- [`docs/RESOLUTION.md`](docs/RESOLUTION.md) — local-first media resolution
- [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) — permissions used and why
- [`docs/STORAGE.md`](docs/STORAGE.md) — where downloads are saved
- [`docs/SECURITY.md`](docs/SECURITY.md) — security model
- [`docs/RELEASE.md`](docs/RELEASE.md) — releases and limitations
