#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s app/src/test/python -v
./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:lintFullDebug :app:lintLiteDebug :app:assembleFullDebug :app:assembleLiteDebug --console=plain "$@"
# CI test downloads use a development signer; tagged releases use the pinned key.
FETCHLY_SIGNING_STORE="${FETCHLY_SIGNING_STORE:-$HOME/.android/debug.keystore}" \
  ./gradlew :app:assembleLiteRelease --console=plain "$@"
