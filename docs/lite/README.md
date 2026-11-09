# Fetchly Lite

Lite is a separate installable Android edition in this repository. Its application ID is `com.fetchly.app.lite` for stable releases and `com.fetchly.app.lite.test` for debug/optimized test builds. Full Fetchly keeps its existing application IDs. Each edition has its own preferences, history database and queue.

## Source boundaries

- `app/src/main/`: extraction, validated URL handling, storage, queue, workers, notifications, database and existing full-app screens.
- `app/src/full/`: full-edition entry point and original broad keep rules.
- `app/src/lite/`: dedicated Lite entry point, UI, format-selection view model, theme, manifests and approved artwork.
- `app/src/testLite/`: Lite flow tests. Shared tests remain in `app/src/test/`.

`MainActivity` and the floating share activity cross the edition boundary through `EditionUi`. Lite release builds use narrower shrinking rules so unused full-app UI and artwork can be removed. The full release keeps its existing ProGuard rules.

## User flow

Welcome appears once. The introduction fades in, and Get started appears after 2.5 seconds. Android's disabled-animation preference skips this delay. Completion is saved before entering Home. Incoming links remain available after welcome.

Home contains link input, Paste and Fetch actions. Pasted text is parsed automatically after a short debounce, cancelling stale resolves when input changes. There is no supported-app grid or site-selection setting. Downloads shows active, paused, failed and cancelled entries. Library contains completed entries, search, media filters, Open, Share and confirmed Delete actions.

The same thumbnail-and-format dialog is used for pasted links and Android social shares. Video choices use actual available source formats. Audio offers MP3 only; direct audio files are downloaded before conversion. Ask every time requires a format selection, except a photo's single original-file option. Silent video requires explicit confirmation.

For a single post whose extractor returns multiple attachments, Lite shows selectable previews. All items start checked; users can select two out of five and adjust each item's format. Selected items queue independently, with one active download at a time. Successfully queued items are removed from a partially failed selection, so retry queues only remaining items. No ZIP option is exposed.

Availability depends on the source and extractor. Attachment fixtures and mock tests do not establish live support for every site's photo carousels. Private, deleted, sign-in-restricted and unsupported posts show errors. Profile and YouTube playlist links are not enabled as bulk-post collections.

Settings contains default quality, Android notification on/off controls, Android app settings, verified engine maintenance, history clearing, release links and About. There is no Appearance page. Notification status is refreshed on return from Android settings; mandatory Android background-task disclosures are controlled by the OS. Permission is requested when downloading, rather than at first launch.

## Visual assets

All backgrounds render at 77% opacity over solid black, with preserved proportions. Opaque parchment panels keep text readable. Home and welcome can scroll so the complete artwork remains available on shorter displays. Downloads and Library use proportional backgrounds behind their lists.

The approved Apollo Home, Hermes Downloads, Athena Library and Hermes welcome images are compressed to 720 × 1601 WebP. Together they total 822,338 bytes. The supplied app logo (`1000026529.png`) is retained as a 512 × 512 launcher resource, with its artwork unchanged apart from scaling. The supplied `fetchly_lite_greek_emblem.svg` is converted into a white Android vector small icon using its dark silhouette; colored regions remain transparent so it can be tinted correctly by Android.

Artwork was generated and approved in the design conversation, then converted from the original PNGs. Source generation filenames: Home `exec-a80f6e0e-e9f7-42b9-8364-17697fa59d11.png`, Downloads `exec-23232aaa-bf75-430e-b8d2-2fc34e2bd260.png`, Library `exec-47c442c1-603d-4950-9527-ddab38f1532f.png`, welcome `exec-ef862f61-85c0-4024-a533-35fef58e8f26.png`. The common direction is original Greek black-figure pottery art inspired by Apotheon, with terracotta, charcoal, incised ochre details, meander borders and space for controls. No game assets are bundled.

## Build and download

Requirements: JDK 17, Android SDK 35 / Build Tools 35.0.0, and a Python interpreter compatible with Chaquopy's build tools (Python 3.12 is used in CI). The bundled Android runtime is Python 3.12. Lite currently supports Android 10+ and **arm64-v8a only**; it does not support 32-bit phones.

```bash
./gradlew :app:assembleLiteDebug           # test package
./gradlew :app:assembleLitePreview         # optimized test package
./gradlew :app:assembleLiteRelease         # unsigned unless explicit production signing is enabled
./gradlew :app:assembleFullDebug
scripts/check.sh
```

When the local default Python is too new for Chaquopy 15's build dependencies, pass `-PfetchlyBuildPython=/absolute/path/to/python3.12` to Gradle or to `scripts/check.sh`.

Debug output is `app/build/outputs/apk/lite/debug/app-lite-debug.apk`; optimized test output is `app/build/outputs/apk/lite/preview/app-lite-preview.apk`. Both use `.test` application IDs, `-test` version names and development signing. `FETCHLY_TEST_SIGNING_STORE` can configure these test builds; it cannot select release signing.

Production signing requires explicit `FETCHLY_PRODUCTION_BUILD=true` and all persistent-key credentials. GitHub release jobs restore the existing pinned key from repository Secrets, verify its certificate before building, and verify the final APK's signature, stable package/version and non-debuggable manifest before upload. There is no generated-key or debug-key fallback for production. See [production signing and Secrets](../RELEASE.md#persistent-production-signing).

**Stable APK:** [fetchly-lite.apk](https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly-lite.apk), downloaded directly from GitHub Releases. [Release notes](../release-notes/v2.4.7-lite.md) · [Production build metadata](https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly-lite-release.json). The Check app workflow's `fetchly-lite-test` Actions artifact remains a separate test channel.

## Size and verification limits

Current production release **2.4.7-lite** from [successful release run 42641069815](https://github.com/mrduhlol/Fetchly/actions/runs/42641069815): **66,548,567 bytes (66.55 MB / 63.47 MiB)**, source commit `34a0e98705ebfb574b19baee94d970092e4117f4`. SHA-256: `50cc6749f2a5e1ee0ca81cd5a571f71abf90beeff654bbf1a43ac40a6e85386a`. The pinned production signer, stable package, non-debuggable manifest and ARM64 architecture passed release verification. Both latest APK links returned HTTP 200 after publication. This release updates both editions; production 2.4.6-lite can update in place.

Historical production release **2.4.6-lite** from [successful release run 42635967953](https://github.com/mrduhlol/Fetchly/actions/runs/42635967953): **66,547,323 bytes (66.55 MB / 63.46 MiB)**. The public APK download returned HTTP 200 without Actions authentication. Its SHA-256 is `a9c6d59b7ef9d2d712ea2d6cdca55f21aa34cfc853e32460dc04c0f1dad5b4d6`, and its certificate matches the production pin `79463eb1458238111e05504594cd26ed70dbc4dd246775251159907076a9ba2b`. The stable package is `com.fetchly.app.lite`, minimum API 29, ARM64 only, and non-debuggable. Original full Fetchly release assets were preserved.

Historical development test artifact from [successful CI run 42612551046](https://github.com/mrduhlol/Fetchly/actions/runs/42612551046), source commit `03d09cda012aea884d88339a9b862a69ef3dd86b`: **66,548,583 bytes (66.55 MB / 63.47 MiB)**. This record predates stable signing and package separation; use the stable download above for production.

APK SHA-256: `fb1c5d11677cda45472f9ce80faeb9e0ae878a6a82790314dae1c7cd5316d268`. That historical APK Signature Scheme v2 verifies with the development certificate and the former test package `com.fetchly.app.lite`, version `2.4.6-lite`, minimum API 29, and only `arm64-v8a` libraries. All packaged XML/resources were checked for `STRING_TOO_LARGE` substitution; none remains. CI development keys are generated per run, so installing a later test build may require removing an earlier test build signed with a different key. Uninstalling clears in-app history and preferences.

Validation: **267 Lite Kotlin tests, 257 full-edition Kotlin tests, and 35 Python tests pass**. Both debug lint checks and the Lite release build pass; lint retains warnings. That historical CI run built full and Lite debug APKs plus an optimized development-signed Lite APK. Current check runs build optimized test APKs as Preview, while production workflows build Release. This verifies compilation and automated behavior, not visual/device behavior.

Python/yt-dlp and FFmpeg remain bundled for on-device extraction, merging, audio conversion and no-backend operation. Simplifying the interface does not remove these engines. Lite reduces architecture packaging, unused UI/resources, artwork size and startup work; it loads extractors on demand and runs one download at a time. APK size is measured from the finished optimized build, not inferred from screen count.

Native visual verification was attempted using the shared device tools, but device startup failed twice with “Agent tools did not start within 30000 ms.” No device UI or live multi-site download claim is made from unit tests or compilation alone. Device checks for the first-run animation, system share sheet, notifications, long lists and real source behavior remain outstanding; production signing does not imply that this device coverage has been completed.
