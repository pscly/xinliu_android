#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/_env.sh"

print_help() {
  cat <<'EOF'
verify.sh：Linux 版本门禁脚本

默认检查（推荐）：
  - :app:assembleDebug
  - :app:testDebugUnitTest
  - :app:lintDebug
  - :app:assembleBenchmark

可选：
  --all  额外构建 baselineprofile 与 macrobenchmark（更慢）

环境变量：
  JAVA_HOME         默认 /usr/lib/jvm/java-21-openjdk-amd64
  GRADLE_USER_HOME  默认 /tmp/gradle-user-home
  ANDROID_USER_HOME 默认 /tmp/android-user-home
EOF
}

all=0
case "${1:-}" in
  --all) all=1; shift ;;
  -h|--help) print_help; exit 0 ;;
esac

cd "$PROJECT_DIR"

common_args=(
  -Pkotlin.compiler.execution.strategy=in-process
  --stacktrace
)

./gradlew :app:assembleDebug "${common_args[@]}" "$@"
./gradlew :app:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :app:lintDebug "${common_args[@]}" "$@"
./gradlew :app:assembleBenchmark "${common_args[@]}" "$@"

if [[ $all -eq 1 ]]; then
  ./gradlew :baselineprofile:assemble "${common_args[@]}" "$@"
  ./gradlew :macrobenchmark:assemble "${common_args[@]}" "$@"
fi

echo "verify.sh: OK"
