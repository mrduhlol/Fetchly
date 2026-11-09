# Release / build

## Requirements

- JDK 17
- Android SDK: `compileSdk 35`, `build-tools 35.0.0`, `minSdk 29` (Android 10+)
- Python 3.12 on `PATH` — Chaquopy builds the bundled engine's pip packages
  with the host interpreter; the host version builds the APK, the packaged
  version runs on the phone
- Gradle 8.7 (or the Gradle wrapper settings in `gradle/wrapper/`)

No environment variables, no backend, no configuration needed.

## Build

```bash
./gradlew :app:assembleFullDebug           # full debug APK
./gradlew :app:assembleLiteRelease         # optimized Lite APK (unsigned without signing config)
./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest
scripts/check.sh                          # both editions, lint, Python tests and test APKs
```

The full debug APK lands at `app/build/outputs/apk/full/debug/app-full-debug.apk`
(applicationId `com.fetchly.app.debug`). Lite release output is under
`app/build/outputs/apk/lite/release/` (applicationId `com.fetchly.app.lite`).
Lite debug uses `com.fetchly.app.lite.debug`. Both editions can coexist.
See [Lite build details](lite/README.md) for compatible host Python selection,
architecture limits and development signing.

## Releases (CI)

Push a tag `v*` (for example `v2.0`). The workflow in
`.github/workflows/release-apk.yml` builds on API 35, runs the unit tests,
and prepares full `fetchly_v<version>.apk` / `fetchly.apk` and Lite
`fetchly-lite_v<version>.apk` / `fetchly-lite.apk` assets for a new GitHub release.
Both APK certificates are checked against the pinned signer. Existing published
release assets remain unchanged. It can also be
started manually with `workflow_dispatch`.

## Known limitations

1. Pause retains partial downloads; actual byte-range continuation depends on the source.
   Some extractor downloads may restart on resume. Cancel and retry are supported.
2. YouTube is fully supported. Other platforms are best effort through the
   same engine, and every failure states its real cause.
3. Stream URLs from extractors expire. A download that fails with 403/410 is
   re-resolved once automatically; if retrying later still fails, paste the
   link again for a fresh resolve (the app says so instead of looping).
4. Debug-signed builds carry a different signing key: uninstall any older
   Fetchly before installing this one.
5. History starts fresh in v2.0 — it never migrates from v1.8.
6. No account system, no cloud sync — by design.

## Pinned signing identity from 2.4

The release workflow supplies `FETCHLY_SIGNING_STORE` explicitly. It does not rely on Gradle's OS-dependent default debug-keystore path. Before publishing, `apksigner` verifies the artifact and its certificate is compared against `docs/SIGNING_CERT_SHA256`; a mismatch stops the workflow. BUILD.txt records the certificate fingerprint. The manual Audit APK signer workflow prints only the persistent key's public certificate fingerprint, never private key material.

The published 2.3 APK used a different certificate from the persistent key, so upgrading to corrected 2.4 requires one uninstall/reinstall. Uninstalling clears app settings and in-app history. Do not claim in-place compatibility with 2.3. Subsequent releases with the pinned certificate support in-place updates.
