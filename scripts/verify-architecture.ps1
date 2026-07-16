[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 架构边界门禁（与 verify-architecture.sh 同一组确定性检查）
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location $projectDir

function Fail([string]$Message) {
  Write-Error "verify-architecture.ps1: FAIL — $Message"
  exit 1
}

function Assert-File([string]$Path, [string]$Message) {
  if (-not (Test-Path -Path $Path -PathType Leaf)) {
    Fail "$Message ($Path)"
  }
}

function Assert-Contains([string]$Path, [string]$Pattern, [string]$Message) {
  if (-not (Test-Path $Path)) {
    Fail "$Message (missing $Path)"
  }
  $hits = Select-String -Path $Path -Pattern $Pattern -SimpleMatch -ErrorAction SilentlyContinue
  if (-not $hits) {
    # 目录时递归
    if (Test-Path $Path -PathType Container) {
      $hits = Get-ChildItem $Path -Recurse -File |
        Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' } |
        Select-String -Pattern $Pattern -SimpleMatch -ErrorAction SilentlyContinue
    }
  }
  if (-not $hits) {
    Fail "$Message (path=$Path pattern=$Pattern)"
  }
}

function Assert-NoMatch {
  param([string]$Path, [string]$Pattern, [string]$Message)
  if (-not (Test-Path $Path)) {
    return
  }
  $matches = @()
  if (Test-Path $Path -PathType Container) {
    $matches = Get-ChildItem $Path -Recurse -File |
      Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' } |
      Select-String -Pattern $Pattern -ErrorAction SilentlyContinue
  } else {
    $matches = Select-String -Path $Path -Pattern $Pattern -ErrorAction SilentlyContinue
  }
  if ($matches) {
    $matches | ForEach-Object { Write-Error $_.ToString() }
    throw $Message
  }
}

function Assert-NoProjectDep([string]$BuildFile, [string]$Dep, [string]$Message) {
  if (Select-String -Path $BuildFile -Pattern "project(`"$Dep`")" -SimpleMatch -Quiet) {
    Fail "$Message ($BuildFile -> $Dep)"
  }
}

# ── 1) 六个新 Core 模块 ──────────────────────────────────
$sixModules = @(
  'core:settings',
  'core:update',
  'core:calendar',
  'core:quicktiles',
  'core:externalactions',
  'core:diagnostics'
)
foreach ($module in $sixModules) {
  Assert-Contains 'settings.gradle.kts' "include(`":$module`")" "缺少模块注册 :$module"
  $dirPath = $module.Replace(':', '/')
  Assert-File "$dirPath/build.gradle.kts" "缺少模块构建文件 :$module"
}

# ── 2) Feature 互不依赖 ──────────────────────────────────
Get-ChildItem 'feature' -Directory | ForEach-Object {
  $build = Join-Path $_.FullName 'build.gradle.kts'
  if (Test-Path $build) {
    Assert-NoMatch -Path $build -Pattern 'project\(":feature:' -Message "Feature 不得依赖其他 Feature: $build"
  }
}

# ── 3) Core 不依赖 app/Feature ───────────────────────────
Get-ChildItem 'core' -Directory | ForEach-Object {
  $build = Join-Path $_.FullName 'build.gradle.kts'
  if (Test-Path $build) {
    Assert-NoMatch -Path $build -Pattern 'project\(":app"\)|project\(":feature:' -Message "Core 不得依赖 app/Feature: $build"
  }
}

# ── 4) :feature:settings 依赖白名单 ──────────────────────
$settingsBuild = 'feature/settings/build.gradle.kts'
Assert-File $settingsBuild '缺少 feature/settings 构建文件'
Assert-Contains $settingsBuild 'project(":core:domain")' 'settings 必须依赖 domain'
Assert-Contains $settingsBuild 'project(":core:navigation")' 'settings 必须依赖 navigation'
Assert-Contains $settingsBuild 'project(":core:designsystem")' 'settings 必须依赖 designsystem'
foreach ($forbidden in @(':core:data', ':core:network', ':core:sync', ':core:update', ':core:calendar', ':core:settings')) {
  Assert-NoProjectDep $settingsBuild $forbidden ":feature:settings 依赖白名单禁止 $forbidden"
}
if (Select-String -Path $settingsBuild -Pattern 'retrofit|workmanager|libs\.retrofit|androidx\.work' -Quiet) {
  Fail ':feature:settings 不得依赖 retrofit/workmanager'
}

# ── 5) 归档归属 Home ─────────────────────────────────────
$homeEntry = 'feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeEntryContributor.kt'
Assert-File $homeEntry '缺少 HomeEntryContributor'
Assert-Contains $homeEntry 'ArchivedKey' 'Home 必须拥有 ArchivedKey'
Assert-Contains $homeEntry 'key is HomeKey || key is ArchivedKey' 'Home owns 必须覆盖 ArchivedKey'
Assert-NoMatch -Path 'settings.gradle.kts' -Pattern 'include\(":feature:archived"\)' -Message '不得注册 :feature:archived'
if (Test-Path 'feature/archived') {
  Fail '不得存在 feature/archived 目录'
}

# ── 6) ViewModel 不含 OneMemosNavigator ──────────────────
$vmHits = Get-ChildItem 'feature' -Recurse -Filter '*ViewModel.kt' -File |
  Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]' -and $_.FullName -notmatch '[\\/]build[\\/]' } |
  Select-String -Pattern 'OneMemosNavigator' -ErrorAction SilentlyContinue
if ($vmHits) {
  $vmHits | ForEach-Object { Write-Error $_.ToString() }
  throw 'ViewModel 源码不得引用 OneMemosNavigator'
}

# ── 7) app 聚合 contributor ──────────────────────────────
Assert-File 'app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt' '缺少 AppEntryContributors'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt' 'appEntryContributors' '必须定义 appEntryContributors'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt' 'SettingsEntryContributor' '必须聚合 SettingsEntryContributor'
Assert-File 'app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt' '缺少 AppNavigationHost'
Assert-NoMatch -Path 'app/src/main' -Pattern 'import\s+cc\.pscly\.onememos\.ui\.feature\.\w+\.\w+Screen\b' -Message 'app 不得直接 import Feature *Screen'
Assert-NoMatch -Path 'app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt' -Pattern '\b\w+Screen\s*\(' -Message 'AppNavigationHost 不得直接调用 Feature Screen'

# ── 8) 七个 Settings 能力唯一绑定 ────────────────────────
Assert-File 'app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt' '缺少 SettingsCapabilityModule'
$binds = @(
  'bindSettingsHubCapability',
  'bindAccountSyncSettingsCapability',
  'bindRecordEditingSettingsCapability',
  'bindReminderCalendarSettingsCapability',
  'bindStorageOfflineSettingsCapability',
  'bindAppearanceInteractionSettingsCapability',
  'bindAboutAdvancedSettingsCapability'
)
foreach ($bind in $binds) {
  $hits = Get-ChildItem 'app/src/main/java/cc/pscly/onememos/di' -Recurse -Filter '*.kt' |
    Select-String -Pattern "fun $bind" -ErrorAction SilentlyContinue
  $count = @($hits).Count
  if ($count -ne 1) {
    $hits | ForEach-Object { Write-Error $_.ToString() }
    throw "能力绑定 $bind 必须恰好一次，实际 $count"
  }
}
Assert-NoMatch -Path 'feature' -Pattern '@Module|@Provides|@Binds|@InstallIn' -Message 'Feature 不得声明 Hilt Module/Provides/Binds/InstallIn'
Assert-NoMatch -Path 'core' -Pattern 'fun bindSettingsHubCapability|fun bindAccountSyncSettingsCapability|fun bindRecordEditingSettingsCapability|fun bindReminderCalendarSettingsCapability|fun bindStorageOfflineSettingsCapability|fun bindAppearanceInteractionSettingsCapability|fun bindAboutAdvancedSettingsCapability' -Message 'Core 不得重复绑定 Settings 能力'

# ── 9) benchmark 仍指向 :app ─────────────────────────────
Assert-Contains 'baselineprofile/build.gradle.kts' 'targetProjectPath = ":app"' 'baselineprofile 必须指向 :app'
Assert-Contains 'macrobenchmark/build.gradle.kts' 'targetProjectPath = ":app"' 'macrobenchmark 必须指向 :app'

# ── 10) 旧 Routes / NavController ────────────────────────
# 仅扫 main 源码；测试里会字面量提到这些符号作为断言文案。
if (Test-Path 'app/src/main/java/cc/pscly/onememos/ui/Routes.kt') {
  Fail 'Routes.kt 不得存在'
}
Assert-NoMatch -Path 'app/src/main' -Pattern 'object Routes|class Routes|androidx\.navigation\.compose|\bNavController\b' -Message 'app 不得残留 Routes/NavController/navigation-compose'
Assert-NoMatch -Path 'feature' -Pattern 'object Routes|class Routes|androidx\.navigation\.compose|\bNavController\b' -Message 'feature 不得残留 Routes/NavController/navigation-compose'

# ── 11) §10.1 不可变字面量 ───────────────────────────────
Assert-Contains 'app/build.gradle.kts' '"cc.pscly.onememos"' 'applicationId 必须为 cc.pscly.onememos'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'one_memos_sync' '同步 unique work 名'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'force_full_sync' '同步输入键 force_full_sync'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'is_periodic' '同步输入键 is_periodic'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'followup_sync' '同步输入键 followup_sync'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'one_memos_periodic_sync' '周期同步名'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'one_memos_rebuild_memo_derived_fields' '派生字段重建任务名'
Assert-Contains 'core/sync/src/main/java/cc/pscly/onememos/worker' 'one_memos_attachment_prefetch' '附件预取任务名'
$db = Get-Content 'core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt' -Raw
if ($db -notmatch 'version\s*=\s*11') {
  Fail 'Room 版本必须为 11'
}
Assert-Contains 'app/src/main/java/cc/pscly/onememos/di/AppModule.kt' 'one_memos.db' '数据库文件名'
Assert-Contains 'app/src/main/AndroidManifest.xml' '${applicationId}.fileprovider' 'FileProvider authority'
Assert-Contains 'app/src/main/res/xml/file_paths.xml' 'share_cards/' 'FileProvider share_cards'
Assert-Contains 'app/src/main/res/xml/file_paths.xml' 'screenshots/' 'FileProvider screenshots'
Assert-Contains 'app/src/main/res/xml/file_paths.xml' 'shared/' 'FileProvider shared'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/MainActivity.kt' 'START_EDITOR_UUID' '外部编辑 extra'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt' 'Configuration.Provider' 'Application WorkManager'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt' 'ImageLoaderFactory' 'Application ImageLoader'
Assert-Contains 'app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt' 'HiltWorkerFactory' 'Application HiltWorkerFactory'

Write-Output 'verify-architecture.ps1: OK'
