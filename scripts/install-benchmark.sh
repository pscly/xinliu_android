#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

print_help() {
  cat <<'EOF'
install-benchmark.sh：安装 benchmark APK 到手机

用法：
  ./scripts/install-benchmark.sh [apkPath]

默认行为：
  - 未指定 apkPath：自动选择 app/build/outputs/apk/benchmark/ 下最新的 .apk
  - 未指定设备：当且仅当 adb 只连接了 1 台 device 时自动选择；否则需要指定

参数：
  --serial <serial>        指定设备序列号（等价于设置 ADB_SERIAL）
  --force-uninstall        遇到签名不一致时自动卸载重装（会清空应用数据，谨慎）
  -h, --help               显示帮助

环境变量：
  ADB_SERIAL=<serial>      指定设备序列号（例如 192.168.12.101:5555）
EOF
}

serial="${ADB_SERIAL:-}"
force_uninstall=0
apk_path=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      serial="${2:-}"
      shift 2
      ;;
    --force-uninstall)
      force_uninstall=1
      shift
      ;;
    -h|--help)
      print_help
      exit 0
      ;;
    *)
      apk_path="$1"
      shift
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  echo "错误：找不到 adb，请先安装 Android Platform Tools。" >&2
  exit 1
fi

apk_dir="${PROJECT_DIR}/app/build/outputs/apk/benchmark"
if [[ -z "$apk_path" ]]; then
  apk_path="$(ls -t "${apk_dir}"/*.apk 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "$apk_path" || ! -f "$apk_path" ]]; then
  echo "错误：找不到可安装的 APK。" >&2
  echo "当前目录：$apk_dir" >&2
  echo "请先执行：./scripts/build-benchmark-apk.sh" >&2
  exit 1
fi

if [[ -z "$serial" ]]; then
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
  if [[ ${#devices[@]} -eq 1 ]]; then
    serial="${devices[0]}"
  elif [[ ${#devices[@]} -eq 0 ]]; then
    echo "错误：未发现在线 adb 设备。" >&2
    echo "提示：可尝试 adb connect <ip:port>，或插 USB。" >&2
    exit 1
  else
    echo "错误：发现多个 adb 设备，请通过 --serial 或 ADB_SERIAL 指定目标设备。" >&2
    adb devices
    exit 1
  fi
fi

set +e
output="$(adb -s "$serial" install -r -d -g "$apk_path" 2>&1)"
code=$?
set -e

printf '%s\n' "$output"

if [[ $code -ne 0 ]]; then
  if printf '%s' "$output" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
    if [[ $force_uninstall -eq 1 ]]; then
      echo "检测到签名不一致，开始卸载重装（会清空应用数据）：cc.pscly.onememos" >&2
      adb -s "$serial" uninstall cc.pscly.onememos || true
      adb -s "$serial" install -r -d -g "$apk_path"
    else
      echo "提示：签名不一致，需要先卸载再安装：" >&2
      echo "  adb -s \"$serial\" uninstall cc.pscly.onememos" >&2
      exit 2
    fi
  else
    exit "$code"
  fi
fi

echo "已安装：$apk_path -> $serial"
