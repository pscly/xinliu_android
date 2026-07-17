#!/usr/bin/env bash
set -euo pipefail

# 说明：
# - 本文件用于统一 Linux 环境下的构建/验证脚本运行环境。
# - 目标是把“JDK21 + 可写 HOME 目录 + Kotlin in-process”的约束固化，避免容器/只读 HOME 导致构建失败。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export PROJECT_DIR

# 固化 JDK21（当前工程在一些环境默认 JDK25 会导致 Gradle/Kotlin DSL 解析失败）
DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

candidate_java_home="${ONE_MEMOS_JAVA_HOME:-${JAVA_HOME:-$DEFAULT_JAVA_HOME}}"

if [[ ! -d "$candidate_java_home" ]]; then
  echo "错误：找不到 JAVA_HOME=$candidate_java_home" >&2
  echo "请安装 OpenJDK 21，或显式设置 ONE_MEMOS_JAVA_HOME/JAVA_HOME。" >&2
  exit 1
fi

if [[ ! -x "$candidate_java_home/bin/java" ]]; then
  echo "错误：$candidate_java_home/bin/java 不存在或不可执行" >&2
  exit 1
fi

# `JAVA_TOOL_OPTIONS` 会让 JVM 在版本行前额外输出提示；不能假定第一行就是版本。
java_version_output="$("$candidate_java_home/bin/java" -version 2>&1 || true)"
java_version_line="$(printf '%s\n' "$java_version_output" | grep -m 1 -E '(^|[[:space:]])(openjdk|java) version "[0-9]+' || true)"
if ! printf '%s' "$java_version_line" | grep -q '"21\.'; then
  echo "错误：检测到 JAVA_HOME=$candidate_java_home 不是 JDK21（${java_version_line:-$java_version_output}）" >&2
  echo "本项目只接受 JDK 21；请安装 OpenJDK 21 或设置正确的 JAVA_HOME。" >&2
  exit 1
fi

export JAVA_HOME="$candidate_java_home"
export PATH="$JAVA_HOME/bin:$PATH"

# 避免写入不可写的 HOME（某些 CI/容器环境下会导致 Gradle/Kotlin/Robolectric 等写缓存失败）
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-user-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/tmp/android-user-home}"

mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"

# 说明：
# - 部分 Android/Gradle 组件会写入 "$user.home/.android"（例如 metrics/analytics.settings）。
# - 在 Codex 沙箱中，/root 往往不在 writable roots，导致写入失败并产生 noisy warning。
# - 注意：Java 的 user.home 来自系统用户信息（/etc/passwd），不一定受 $HOME 影响，因此这里同时兜底：
#   1) 尝试让 $HOME 指向可写目录（兼容少数依赖 $HOME 的工具）
#   2) 必要时通过 JVM 参数强制 -Duser.home 指向 ANDROID_USER_HOME（兼容依赖 user.home 的工具）

append_java_tool_options() {
  local opt="$1"
  if [[ "${JAVA_TOOL_OPTIONS:-}" == *"$opt"* ]]; then
    return 0
  fi
  if [[ -z "${JAVA_TOOL_OPTIONS:-}" ]]; then
    export JAVA_TOOL_OPTIONS="$opt"
  else
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} $opt"
  fi
}

# 兜底：尽量避免把文件写到 /root（在沙箱里可能不可写）。
if [[ -z "${HOME:-}" || "${HOME:-}" == "/root" || "${HOME:-}" == /root/* ]]; then
  export HOME="$ANDROID_USER_HOME"
fi

# 若当前用户是 root：强制把 user.home 重定向到可写目录（ANDROID_USER_HOME），
# 避免在受限环境（例如 Codex 沙箱）里写 /root/.android 失败导致 metrics 初始化告警。
if [[ "$(id -u)" == "0" ]]; then
  mkdir -p "${ANDROID_USER_HOME}/.android" >/dev/null 2>&1 || true
  append_java_tool_options "-Duser.home=${ANDROID_USER_HOME}"
fi
