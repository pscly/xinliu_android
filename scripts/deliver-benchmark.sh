#!/usr/bin/env bash
set -euo pipefail

# 一键交付：构建 benchmark -> 生成时间戳 APK -> 安装到手机 -> 推送到 Download
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/build-benchmark-apk.sh" "$@"
"${SCRIPT_DIR}/install-benchmark.sh"
"${SCRIPT_DIR}/push-benchmark-to-download.sh"

echo "deliver-benchmark.sh: OK"
