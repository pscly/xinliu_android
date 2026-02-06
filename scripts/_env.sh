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

java_version_line="$("$candidate_java_home/bin/java" -version 2>&1 | head -n 1 || true)"
if ! printf '%s' "$java_version_line" | grep -q '"21\.'; then
  # 只有在用户显式设置（或系统默认）不是 JDK21 时才兜底到默认 JDK21
  if [[ "$candidate_java_home" != "$DEFAULT_JAVA_HOME" ]]; then
    echo "警告：检测到 JAVA_HOME=$candidate_java_home 不是 JDK21（$java_version_line），将改用默认：$DEFAULT_JAVA_HOME" >&2
    candidate_java_home="$DEFAULT_JAVA_HOME"
    if [[ ! -x "$candidate_java_home/bin/java" ]]; then
      echo "错误：默认 JDK21 不可用：$candidate_java_home/bin/java" >&2
      exit 1
    fi
  fi
fi

export JAVA_HOME="$candidate_java_home"
export PATH="$JAVA_HOME/bin:$PATH"

# 避免写入不可写的 HOME（某些 CI/容器环境下会导致 Gradle/Kotlin/Robolectric 等写缓存失败）
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-user-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/tmp/android-user-home}"

mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
