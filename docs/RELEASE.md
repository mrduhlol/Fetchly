# Release / build

## Requirements

JDK 17, Android SDK 35 / Build Tools 35.0.0, Python 3.12 and the Gradle wrapper are used in CI. The app supports Android 10 (API 29)+. If the local Python default is too new for Chaquopy, add `-PfetchlyBuildPython=/absolute/path/to/python3.12` to Gradle or `scripts/check.sh`.

## Stable and test packages

| Edition | Stable release ID | Debug / optimized test ID |
| --- | --- | --- |
| Fetchly | `com.fetchly.app.debug` | `com.fetchly.app.debug.test` |
| Fetchly Lite | `com.fetchly.app.lite` | `com.fetchly.app.lite.test` |

The full app's stable ID retains its historical `.debug` spelling so existing installations can update. New production APKs use the **release** build type and are not debuggable. Existing published full-app assets remain unchanged; this rule applies to newly built production APKs. The package spelling does not select a development signer. Test builds have a `-test` version suffix and separate application IDs, so they can coexist with stable apps.

```bash
scripts/check.sh                          # tests, lint, debug APKs and optimized Lite test APK
./gradlew :app:assembleLitePreview         # optimized test APK, development signing only
./gradlew :app:assembleFullDebug            # full test APK
./gradlew :app:assembleLiteRelease          # unsigned unless production signing was explicitly enabled
```

Optimized test output is `app/build/outputs/apk/lite/preview/app-lite-preview.apk`. The Check app workflow uploads it as `fetchly-lite-test`; it never publishes GitHub Releases or receives production signing Secrets. `scripts/check.sh` generates a temporary **test** key when `FETCHLY_TEST_SIGNING_STORE` is absent. That variable can sign only debug/preview builds. Production signing is not a fallback for either test build type.

## Persistent production signing

Production workflows use these repository **GitHub Secrets**:

| Secret | Purpose |
| --- | --- |
| `FETCHLY_RELEASE_KEYSTORE_BASE64` | Preferred name for the base64-encoded persistent keystore, when configured |
| `FETCHLY_DEBUG_KEYSTORE_BASE64` | Existing legacy name of this repository's persistent production keystore; used when the preferred name is absent |
| `FETCHLY_RELEASE_STORE_PASSWORD` | Existing persistent keystore password |
| `FETCHLY_RELEASE_KEY_ALIAS` | Existing signing-key alias |
| `FETCHLY_RELEASE_KEY_PASSWORD` | Existing private-key password |

The historical keystore Secret and alias names do not mean that production regenerates an Android debug key. This repository reuses the existing persistent certificate pinned in `docs/SIGNING_CERT_SHA256`, preserving update compatibility. Do not rotate or replace it just to rename a Secret.

`scripts/prepare-production-signing.sh` requires the keystore and all credentials, restores it with private permissions under `RUNNER_TEMP`, and compares the actual certificate against the pin before building. Missing credentials, a wrong password or a wrong certificate stop the job. It never generates a production key or substitutes a test key.

The production jobs map the credential Secrets to `FETCHLY_SIGNING_STORE_PASSWORD`, `FETCHLY_SIGNING_KEY_ALIAS` and `FETCHLY_SIGNING_KEY_PASSWORD`. Only after successful restoration do they export `FETCHLY_SIGNING_STORE` and `FETCHLY_PRODUCTION_BUILD=true`. Gradle's release build uses production signing only when this explicit production mode is enabled and every credential is present. Otherwise it remains unsigned. Debug and preview builds cannot select the production configuration.

After building, `scripts/verify-production-apk.py` verifies the APK signature, single pinned signer, exact stable package/version, non-debuggable manifest and Lite's ARM64 architecture. Only verified APKs are uploaded. Keystores/passwords are never release assets, committed files or test artifacts; workflows remove the restored private keystore even after failure.

The manual **Audit APK signer** workflow validates the same Secrets and reports only the public certificate fingerprint.

## Publishing stable releases

**Release APK** (`release-apk.yml`) runs on new `v*` tags or manual dispatch. It builds both `FullRelease` and `LiteRelease`, runs tests/lint and verifies both production APKs. New releases include `fetchly.apk`, `fetchly-lite.apk`, versioned copies, SHA-256 checksums and public build metadata. Already published release assets remain unchanged.

**Release Fetchly Lite** (`release-lite.yml`) supports adding the first Lite APK to an existing latest stable Fetchly release. Its `release_tag` must name the current published stable release and match the APK version; matching Lite release notes must exist. It refuses to replace an existing published `fetchly-lite.apk`. This allows adding Lite without replacing the full app's APK, versioned APK, build record or checksums.

Stable public download URLs:

- Full: `https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly.apk`
- Lite: `https://github.com/mrduhlol/Fetchly/releases/latest/download/fetchly-lite.apk`

Lite's `fetchly-lite-release.json` records the exact APK size, SHA-256, package/version, minimum API, architecture, production certificate and source commit. `fetchly-lite.sha256` and `fetchly-lite-release-notes.md` are separate Lite assets when adding Lite to an existing release. README download details should use the published metadata, not an estimate or Actions artifact size.

## Compatibility and limits

Production Lite targets ARM64 phones; test and stable APKs remain separate from full Fetchly. The original Actions Lite test used the stable package ID with a development signer. Remove that **earlier Lite test app** before installing the first production Lite APK; uninstalling clears its preferences/history. New `.test` builds coexist with stable Lite.

Published full Fetchly 2.3 used a different certificate from corrected 2.4+, so its documented one-time uninstall/reinstall remains necessary. The existing pinned certificate is preserved for later full-app updates.

Pause retains partial downloads, but actual continuation depends on the source. Public-site extraction remains best effort; private, deleted, restricted or unsupported media can fail. Production signing and automated checks do not substitute for device UI/live-source coverage; see [Lite verification limits](lite/README.md#size-and-verification-limits).
