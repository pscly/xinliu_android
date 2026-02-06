#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/_env.sh"

cd "$PROJECT_DIR"

./gradlew :app:assembleBenchmark \
  -Pkotlin.compiler.execution.strategy=in-process \
  --stacktrace \
  "$@"

"${SCRIPT_DIR}/copy-benchmark-apk.sh"
