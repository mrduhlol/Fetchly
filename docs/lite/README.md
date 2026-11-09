# Fetchly Lite

Lite is a separate installable Android edition in this repository. Its application ID is `com.fetchly.app.lite` for release builds and `com.fetchly.app.lite.debug` for debug builds. Full Fetchly keeps its existing application IDs. Each edition has its own preferences, history database and queue.

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
./gradlew :app:assembleLiteDebug
./gradlew :app:assembleLiteRelease
./gradlew :app:assembleFullDebug
scripts/check.sh
```

When the local default Python is too new for Chaquopy 15's build dependencies, pass `-PfetchlyBuildPython=/absolute/path/to/python3.12` to Gradle or to `scripts/check.sh`.

Debug output: `app/build/outputs/apk/lite/debug/app-lite-debug.apk`. An unsigned optimized release is produced without a signing configuration. Supplying `FETCHLY_SIGNING_STORE` signs the release with the existing configured identity. For local test builds only, the standard Android development keystore can be supplied explicitly. Never distribute that test signer as the pinned production identity.

The Check app workflow publishes a `fetchly-lite-test` artifact containing an optimized APK signed with a development key. The tagged release workflow builds both editions and verifies both APKs against the pinned production certificate before preparing `fetchly.apk` and `fetchly-lite.apk`. Already published assets are left unchanged. README links direct users to test artifacts until a new tagged release includes Lite.

## Size and verification limits

Python/yt-dlp and FFmpeg remain bundled for on-device extraction, merging, audio conversion and no-backend operation. Simplifying the interface does not remove these engines. Lite reduces architecture packaging, unused UI/resources, artwork size and startup work; it loads extractors on demand and runs one download at a time. APK size is measured from the finished optimized build, not inferred from screen count.

Native visual verification was attempted using the shared device tools, but device startup failed twice with “Agent tools did not start within 30000 ms.” No device UI or live multi-site download claim is made from unit tests or compilation alone. Device checks remain necessary for the first-run animation, system share sheet, notifications, long lists and real source behavior before a production release.
