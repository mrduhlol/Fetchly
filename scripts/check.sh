#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s app/src/test/python -v
./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:lintFullDebug :app:lintLiteDebug :app:assembleFullDebug :app:assembleLiteDebug --console=plain --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' "$@"
# CI test downloads use a development signer; tagged releases use the pinned key.
FETCHLY_SIGNING_STORE="${FETCHLY_SIGNING_STORE:-$HOME/.android/debug.keystore}" \
  ./gradlew :app:assembleLiteRelease --console=plain --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' "$@"
