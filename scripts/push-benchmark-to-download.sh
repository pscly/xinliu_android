#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

print_help() {
  cat <<'EOF'
push-benchmark-to-download.sh：把 benchmark APK 推送到手机 Download 目录（便于分发）

用法：
  ./scripts/push-benchmark-to-download.sh [apkPath]

环境变量：
  ADB_SERIAL=<serial>  指定设备序列号（例如 192.168.12.101:5555）
EOF
}

serial="${ADB_SERIAL:-}"
apk_path="${1:-}"

if [[ "${apk_path:-}" == "-h" || "${apk_path:-}" == "--help" ]]; then
  print_help
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "错误：找不到 adb，请先安装 Android Platform Tools。" >&2
  exit 1
fi

apk_dir="${PROJECT_DIR}/app/build/outputs/apk/benchmark"
if [[ -z "$apk_path" ]]; then
  apk_path="$(ls -t "${apk_dir}"/*.apk 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "$apk_path" || ! -f "$apk_path" ]]; then
  echo "错误：找不到 APK：$apk_path" >&2
  echo "当前目录：$apk_dir" >&2
  exit 1
fi

if [[ -z "$serial" ]]; then
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
  if [[ ${#devices[@]} -eq 1 ]]; then
    serial="${devices[0]}"
  elif [[ ${#devices[@]} -eq 0 ]]; then
    echo "错误：未发现在线 adb 设备。" >&2
    exit 1
  else
    echo "错误：发现多个 adb 设备，请通过 ADB_SERIAL 指定目标设备。" >&2
    adb devices
    exit 1
  fi
fi

base="$(basename "$apk_path")"
dst_name="1memos-benchmark-${base}"
remote="/sdcard/Download/${dst_name}"

adb -s "$serial" push "$apk_path" "$remote"
echo "$remote"
