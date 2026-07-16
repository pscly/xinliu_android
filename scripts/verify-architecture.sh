#!/usr/bin/env bash
# 架构边界门禁：模块清单、依赖方向、Settings 白名单、Navigator/Routes/Hilt 残留、§10.1 字面量。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/_env.sh"

cd "$PROJECT_DIR"

fail() {
  echo "verify-architecture.sh: FAIL — $*" >&2
  exit 1
}

assert_file() {
  local path="$1"
  local message="$2"
  [[ -f "$path" ]] || fail "$message ($path)"
}

assert_contains() {
  local path="$1"
  local pattern="$2"
  local message="$3"
  if ! rg -q --fixed-strings "$pattern" "$path"; then
    fail "$message (file=$path pattern=$pattern)"
  fi
}

assert_no_match() {
  local path="$1"
  local pattern="$2"
  local message="$3"
  local matches
  if [[ -d "$path" ]]; then
    matches="$(rg -n --glob '!**/build/**' "$pattern" "$path" || true)"
  elif [[ -f "$path" ]]; then
    matches="$(rg -n "$pattern" "$path" || true)"
  else
    return 0
  fi
  if [[ -n "$matches" ]]; then
    printf '%s\n' "$matches" >&2
    fail "$message"
  fi
}

assert_no_project_dep() {
  local build_file="$1"
  local dep="$2"
  local message="$3"
  if rg -q --fixed-strings "project(\"$dep\")" "$build_file" 2>/dev/null; then
    fail "$message ($build_file -> $dep)"
  fi
}

# ── 1) 六个新 Core 模块 ──────────────────────────────────
SIX_MODULES=(
  "core:settings"
  "core:update"
  "core:calendar"
  "core:quicktiles"
  "core:externalactions"
  "core:diagnostics"
)
for module in "${SIX_MODULES[@]}"; do
  assert_contains "settings.gradle.kts" "include(\":${module}\")" "缺少模块注册 :${module}"
  dir_path="${module//://}"
  assert_file "${dir_path}/build.gradle.kts" "缺少模块构建文件 :${module}"
done

# ── 2) Feature 互不依赖 ──────────────────────────────────
for build in feature/*/build.gradle.kts; do
  [[ -f "$build" ]] || continue
  if rg -q 'project\(":feature:' "$build"; then
    rg -n 'project\(":feature:' "$build" >&2 || true
    fail "Feature 不得依赖其他 Feature: $build"
  fi
done

# ── 3) Core 不依赖 app/Feature ───────────────────────────
for build in core/*/build.gradle.kts; do
  [[ -f "$build" ]] || continue
  if rg -q 'project\(":app"\)|project\(":feature:' "$build"; then
    rg -n 'project\(":app"\)|project\(":feature:' "$build" >&2 || true
    fail "Core 不得依赖 app/Feature: $build"
  fi
done

# ── 4) :feature:settings 依赖白名单 ──────────────────────
SETTINGS_BUILD="feature/settings/build.gradle.kts"
assert_file "$SETTINGS_BUILD" "缺少 feature/settings 构建文件"
assert_contains "$SETTINGS_BUILD" 'project(":core:domain")' "settings 必须依赖 domain"
assert_contains "$SETTINGS_BUILD" 'project(":core:navigation")' "settings 必须依赖 navigation"
assert_contains "$SETTINGS_BUILD" 'project(":core:designsystem")' "settings 必须依赖 designsystem"
for forbidden in ":core:data" ":core:network" ":core:sync" ":core:update" ":core:calendar" ":core:settings"; do
  assert_no_project_dep "$SETTINGS_BUILD" "$forbidden" ":feature:settings 依赖白名单禁止 $forbidden"
done
if rg -qi 'retrofit|workmanager|libs\.retrofit|androidx\.work' "$SETTINGS_BUILD"; then
  fail ":feature:settings 不得依赖 retrofit/workmanager"
fi

# ── 5) 归档归属 Home，无 :feature:archived ───────────────
HOME_ENTRY="feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeEntryContributor.kt"
assert_file "$HOME_ENTRY" "缺少 HomeEntryContributor"
assert_contains "$HOME_ENTRY" "ArchivedKey" "Home 必须拥有 ArchivedKey"
assert_contains "$HOME_ENTRY" "key is HomeKey || key is ArchivedKey" "Home owns 必须覆盖 ArchivedKey"
if rg -q 'include\(":feature:archived"\)' settings.gradle.kts; then
  fail "不得注册 :feature:archived"
fi
if [[ -d feature/archived ]]; then
  fail "不得存在 feature/archived 目录"
fi

# ── 6) ViewModel 不含 OneMemosNavigator ──────────────────
# 注意：rg 多个 --glob 为 OR；必须先定位 ViewModel 文件再过滤路径。
vm_hits="$(
  rg -l --glob '*ViewModel.kt' --glob '!**/build/**' 'OneMemosNavigator' feature 2>/dev/null \
    | while IFS= read -r f; do
        case "$f" in
          */src/main/*) printf '%s\n' "$f" ;;
        esac
      done \
    | while IFS= read -r f; do
        rg -n 'OneMemosNavigator' "$f" || true
      done || true
)"
if [[ -n "$vm_hits" ]]; then
  printf '%s\n' "$vm_hits" >&2
  fail "ViewModel 源码不得引用 OneMemosNavigator"
fi

# ── 7) app 聚合 contributor，不直接调用 Feature Screen ──
assert_file "app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt" \
  "缺少 AppEntryContributors"
assert_contains "app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt" \
  "appEntryContributors" "必须定义 appEntryContributors"
assert_contains "app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt" \
  "SettingsEntryContributor" "必须聚合 SettingsEntryContributor"
assert_file "app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt" \
  "缺少 AppNavigationHost"
if rg -n --glob '!**/build/**' 'import\s+cc\.pscly\.onememos\.ui\.feature\.\w+\.\w+Screen\b' app/src/main; then
  fail "app 不得直接 import Feature *Screen"
fi
if rg -n '\b\w+Screen\s*\(' app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt; then
  fail "AppNavigationHost 不得直接调用 Feature Screen"
fi

# ── 8) 七个 Settings 能力唯一绑定 ────────────────────────
CAPABILITY_MODULE="app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt"
assert_file "$CAPABILITY_MODULE" "缺少 SettingsCapabilityModule"
BINDS=(
  "bindSettingsHubCapability"
  "bindAccountSyncSettingsCapability"
  "bindRecordEditingSettingsCapability"
  "bindReminderCalendarSettingsCapability"
  "bindStorageOfflineSettingsCapability"
  "bindAppearanceInteractionSettingsCapability"
  "bindAboutAdvancedSettingsCapability"
)
for bind in "${BINDS[@]}"; do
  count="$(rg -n --glob '!**/build/**' "fun ${bind}" app/src/main/java/cc/pscly/onememos/di | wc -l | tr -d ' ')"
  if [[ "$count" != "1" ]]; then
    rg -n --glob '!**/build/**' "fun ${bind}" app/src/main/java/cc/pscly/onememos/di >&2 || true
    fail "能力绑定 ${bind} 必须恰好一次，实际 ${count}"
  fi
done
assert_no_match "feature" '@Module|@Provides|@Binds|@InstallIn' \
  "Feature 不得声明 Hilt Module/Provides/Binds/InstallIn"
# Core 内不得再绑定七个 Settings 能力接口到实现
assert_no_match "core" \
  'fun bindSettingsHubCapability|fun bindAccountSyncSettingsCapability|fun bindRecordEditingSettingsCapability|fun bindReminderCalendarSettingsCapability|fun bindStorageOfflineSettingsCapability|fun bindAppearanceInteractionSettingsCapability|fun bindAboutAdvancedSettingsCapability' \
  "Core 不得重复绑定 Settings 能力"

# ── 9) benchmark 仍指向 :app ─────────────────────────────
assert_contains "baselineprofile/build.gradle.kts" 'targetProjectPath = ":app"' \
  "baselineprofile 必须指向 :app"
assert_contains "macrobenchmark/build.gradle.kts" 'targetProjectPath = ":app"' \
  "macrobenchmark 必须指向 :app"

# ── 10) 旧 Routes / NavController / navigation-compose ──
# 仅扫 main 源码；测试里会字面量提到这些符号作为断言文案。
if [[ -f app/src/main/java/cc/pscly/onememos/ui/Routes.kt ]]; then
  fail "Routes.kt 不得存在"
fi
assert_no_match "app/src/main" 'object Routes|class Routes|androidx\.navigation\.compose|\bNavController\b' \
  "app 不得残留 Routes/NavController/navigation-compose"
assert_no_match "feature" 'object Routes|class Routes|androidx\.navigation\.compose|\bNavController\b' \
  "feature 不得残留 Routes/NavController/navigation-compose"

# ── 11) §10.1 不可变字面量（与 ImmutableRegressionContractsTest 对齐）──
assert_contains "app/build.gradle.kts" '"cc.pscly.onememos"' "applicationId 必须为 cc.pscly.onememos"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "one_memos_sync" "同步 unique work 名"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "force_full_sync" "同步输入键 force_full_sync"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "is_periodic" "同步输入键 is_periodic"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "followup_sync" "同步输入键 followup_sync"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "one_memos_periodic_sync" "周期同步名"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "one_memos_rebuild_memo_derived_fields" "派生字段重建任务名"
assert_contains "core/sync/src/main/java/cc/pscly/onememos/worker" "one_memos_attachment_prefetch" "附件预取任务名"
if ! rg -q 'version\s*=\s*11' core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt; then
  fail "Room 版本必须为 11"
fi
assert_contains "app/src/main/java/cc/pscly/onememos/di/AppModule.kt" "one_memos.db" "数据库文件名"
assert_contains "app/src/main/AndroidManifest.xml" '${applicationId}.fileprovider' "FileProvider authority"
assert_contains "app/src/main/res/xml/file_paths.xml" "share_cards/" "FileProvider share_cards"
assert_contains "app/src/main/res/xml/file_paths.xml" "screenshots/" "FileProvider screenshots"
assert_contains "app/src/main/res/xml/file_paths.xml" "shared/" "FileProvider shared"
assert_contains "app/src/main/java/cc/pscly/onememos/MainActivity.kt" "START_EDITOR_UUID" "外部编辑 extra"
assert_contains "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt" "Configuration.Provider" "Application WorkManager"
assert_contains "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt" "ImageLoaderFactory" "Application ImageLoader"
assert_contains "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt" "HiltWorkerFactory" "Application HiltWorkerFactory"

echo "verify-architecture.sh: OK"
