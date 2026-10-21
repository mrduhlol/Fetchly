<div align="center">

<img src="docs/fetchly-mark.svg" width="120" alt="Fetchly logo" />

# Fetchly

**Paste. Choose. Fetch.**

![version](https://img.shields.io/github/v/release/mrduhlol/Fetchly) — Latest: **v2.0**

Paste a public media link, pick a quality, save it to your device.

[![Download APK](https://img.shields.io/badge/Download-APK-3B82F6?style=for-the-badge&logo=android)](https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly_v2.0.apk)

</div>

## What it does

1. Paste a link — YouTube (video, Shorts, audio-only), another site, or a direct media file
2. Fetchly resolves it **on your phone** with a bundled, current yt-dlp engine and shows what's actually there
3. Pick a quality and download it to `Movies/Fetchly/`, `Music/Fetchly/` or `Pictures/Fetchly/`

No backend, no accounts, no API keys, no configuration — resolution, downloading and merging all run on-device.

## Coming from v1.8?

v1.8 shipped a yt-dlp engine pinned in 2024. YouTube eventually started refusing
it ("Please sign in"), and v1.8 surfaced that as a generic *"Fetchly couldn't
retrieve media information from this link."* v2.0 is a from-scratch rebuild
with a current engine (2026.08.19) — and every failure now names its real
cause (sign-in demand, private or deleted video, geo-block, expired link,
full disk, merge failure) instead of one catch-all sentence.

## Honest scope

- **YouTube**: public video support — video with sound (streams are fetched and muxed
  on-device), Shorts, and audio-only. Formats YouTube gates behind a
  JavaScript runtime aren't offered, because Fetchly doesn't ship one.
- **Other platforms** (Instagram, TikTok, X, Reddit, Facebook, Pinterest, LinkedIn):
  best effort through the same engine. When a post isn't publicly
  downloadable, the error says exactly why.
- **Direct links** (mp4, mp3, jpg, …): downloaded as-is, verified by magic
  bytes and size before being reported as saved.
- Only publicly accessible media where downloading is permitted.

## Install

- **Uninstall any older Fetchly first.** These are debug-signed builds with a
  different signing key, so Android will refuse to update over an old install.
- The APK is about 124 MB — it carries the Python engine and ffmpeg for
  on-device merging.
- Needs Android 10 (API 29) or newer.

## Build it yourself

Needs JDK 17, the Android SDK (compileSdk 35, build-tools 35.0.0) and
Python 3.12 on `PATH` (Chaquopy uses the host interpreter to build the
bundled engine).

```bash
./gradlew :app:assembleDebug        # APK
scripts/check.sh                   # Python + Kotlin tests, Android lint, APK
```

## Docs

- [`docs/RESOLUTION.md`](docs/RESOLUTION.md) — how a link becomes a download
- [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) — permissions used and why
- [`docs/STORAGE.md`](docs/STORAGE.md) — where downloads and history live
- [`docs/SECURITY.md`](docs/SECURITY.md) — security model
- [`docs/RELEASE.md`](docs/RELEASE.md) — releases and limitations

## Reliability updates

Pasted share text is normalized to its media link. The preview lists available video,
audio and file choices with container and reported size. Single-format extractors and
multi-video posts are handled consistently; multi-video links currently select the
first item. Direct image links display an image preview. Audio-only choices are saved
to Music, with explicit stream mapping when merging.

Checks run on pushes and pull requests. See [verification notes](docs/VERIFICATION.md)
for the tested sources and the limits of live platform checks.
