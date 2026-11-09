#!/usr/bin/env bash
# Run only in a production release job. Never generates or substitutes a key.
set -euo pipefail
: "${FETCHLY_KEYSTORE_BASE64:?Missing persistent release keystore secret}"
: "${FETCHLY_SIGNING_STORE_PASSWORD:?Missing release store password secret}"
: "${FETCHLY_SIGNING_KEY_ALIAS:?Missing release key alias secret}"
: "${FETCHLY_SIGNING_KEY_PASSWORD:?Missing release key password secret}"
: "${RUNNER_TEMP:?Missing runner temporary directory}"
: "${GITHUB_ENV:?Missing GitHub environment file}"
umask 077
fetchly_production_store="$RUNNER_TEMP/fetchly-production-signing.jks"
printf '%s' "$FETCHLY_KEYSTORE_BASE64" | base64 --decode > "$fetchly_production_store"
fetchly_expected_cert=$(tr -d '[:space:]' < docs/SIGNING_CERT_SHA256)
fetchly_actual_cert=$(keytool -exportcert -keystore "$fetchly_production_store" \
  -alias "$FETCHLY_SIGNING_KEY_ALIAS" -storepass:env FETCHLY_SIGNING_STORE_PASSWORD | sha256sum | cut -d ' ' -f 1)
if [[ "$fetchly_actual_cert" != "$fetchly_expected_cert" ]]; then
  rm -f -- "$fetchly_production_store"
  echo 'Persistent signing certificate does not match the pinned production identity.' >&2
  exit 1
fi
printf 'FETCHLY_SIGNING_STORE=%s\nFETCHLY_PRODUCTION_BUILD=true\n' "$fetchly_production_store" >> "$GITHUB_ENV"
echo 'Persistent production signing identity verified.'
