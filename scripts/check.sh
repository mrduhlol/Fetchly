#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s app/src/test/python -v
./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:lintFullDebug :app:lintLiteDebug :app:assembleFullDebug :app:assembleLiteDebug --console=plain "$@"
