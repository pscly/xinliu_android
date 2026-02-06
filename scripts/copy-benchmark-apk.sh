#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

src="${PROJECT_DIR}/app/build/outputs/apk/benchmark/app-benchmark.apk"
dst_dir="$(dirname "$src")"

if [[ ! -f "$src" ]]; then
  echo "错误：找不到 APK：$src" >&2
  echo "请先执行 benchmark 构建：./gradlew :app:assembleBenchmark" >&2
  exit 1
fi

ts="$(date '+%Y-%m-%dT%H-%M-%S')"
dst="${dst_dir}/${ts}.apk"

cp -f "$src" "$dst"
echo "$dst"
