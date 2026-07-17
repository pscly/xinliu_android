#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/_env.sh"

print_help() {
  cat <<'EOF'
verify.sh：Linux 版本门禁脚本

默认检查（推荐）：
  - scripts/verify-architecture.sh（模块边界 / 依赖方向 / §10.1）
  - :app:assembleDebug
  - :app:testDebugUnitTest
  - :feature:settings:testDebugUnitTest
  - :core:settings:testDebugUnitTest
  - :core:navigation:testDebugUnitTest
  - :core:update:testDebugUnitTest
  - :core:calendar:testDebugUnitTest
  - :core:quicktiles:testDebugUnitTest
  - :core:externalactions:testDebugUnitTest
  - :core:diagnostics:testDebugUnitTest
  - :feature:home:testDebugUnitTest
  - :feature:collections:testDebugUnitTest
  - :app:lintDebug
  - :app:assembleBenchmark
  - :baselineprofile:assembleBenchmark
  - :macrobenchmark:assembleBenchmark

可选：
  --all  额外执行设备相关扩展（当前与默认 assemble 一致；保留开关供后续接真机任务）

环境变量：
  JAVA_HOME         默认 /usr/lib/jvm/java-21-openjdk-amd64
  GRADLE_USER_HOME  默认 /tmp/gradle-user-home
  ANDROID_USER_HOME 默认 /tmp/android-user-home

稳定发布顺序（不由本脚本完成推送/Release）：
  完整门禁 → 推送 main → 推送 vMAJOR.MINOR.PATCH Tag → 等待 Tag Actions
  → 复核 Artifact → 人工创建非草稿 latest Release
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

# GitHub Actions runner 内存有限：降低并行峰值，减少 D8 merge / 测试堆叠加。
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  common_args+=(--max-workers=2)
fi

# 1) 架构边界（快速失败，不启动完整 Gradle 测试）
"${SCRIPT_DIR}/verify-architecture.sh"

# 2) Debug 构建与单元测试
./gradlew :app:assembleDebug "${common_args[@]}" "$@"
# 经验坑：assembleDebug 的 D8 之后 daemon 可能已接近堆上限；先停再跑测试。
./gradlew --stop >/dev/null 2>&1 || true
./gradlew :app:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :feature:settings:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:settings:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:navigation:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:update:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:calendar:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:quicktiles:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:externalactions:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :core:diagnostics:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :feature:home:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :feature:collections:testDebugUnitTest "${common_args[@]}" "$@"
./gradlew :app:lintDebug "${common_args[@]}" "$@"

# 经验坑：lint/debug 等任务跑完后，Gradle daemon 可能因内存碎片化导致 benchmark 的 D8 mergeDex OOM。
./gradlew --stop >/dev/null 2>&1 || true

# 3) Benchmark 与 profile 模块
./gradlew :app:assembleBenchmark "${common_args[@]}" "$@"
./gradlew :baselineprofile:assembleBenchmark "${common_args[@]}" "$@"
./gradlew :macrobenchmark:assembleBenchmark "${common_args[@]}" "$@"

if [[ $all -eq 1 ]]; then
  # 预留：设备相关扩展（当前默认已含 assembleBenchmark）
  true
fi

echo "verify.sh: OK"
