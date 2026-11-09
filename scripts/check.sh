#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s app/src/test/python -v
python3 -m unittest discover -s scripts -p 'test_*.py' -v
# Verification artifacts use an ephemeral development key unless a signer was supplied.
# Do not assume Android's OS-dependent default debug-keystore location exists.
if [[ -z "${FETCHLY_TEST_SIGNING_STORE:-}" ]]; then
  fetchly_test_signing_dir=$(mktemp -d "${TMPDIR:-/tmp}/fetchly-lite-signing.XXXXXX")
  trap 'rm -rf -- "$fetchly_test_signing_dir"' EXIT
  export FETCHLY_TEST_SIGNING_STORE="$fetchly_test_signing_dir/development.jks"
  keytool -genkeypair -keystore "$FETCHLY_TEST_SIGNING_STORE" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 \
    -dname 'CN=Fetchly Development,O=Fetchly,C=US' -noprompt
fi
./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:lintFullDebug :app:lintLiteDebug :app:assembleFullDebug :app:assembleLiteDebug --console=plain --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' "$@"
./gradlew :app:assembleLitePreview --console=plain --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' "$@"
