[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 以脚本所在目录为锚点，定位到工程根目录（scripts/ 的父目录）
$projectDir = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $projectDir 'gradlew.bat'

if (-not (Test-Path -Path $gradlew)) {
  throw "找不到 Gradle Wrapper：$gradlew"
}

function Resolve-Adb {
  $cmd = Get-Command adb -ErrorAction SilentlyContinue
  if ($cmd -and $cmd.Source) {
    return $cmd.Source
  }

  # 兼容仓库/工作区内置 android-sdk（常见：<root>/android-sdk/platform-tools/adb.exe）
  $localAdb1 = Join-Path $projectDir 'android-sdk\platform-tools\adb.exe'
  if (Test-Path -Path $localAdb1) {
    return $localAdb1
  }

  # 兼容工程位于子目录时（例如 <workspace>/1memos/），adb 在工作区根目录。
  $workspaceDir = Split-Path -Parent $projectDir
  $localAdb2 = Join-Path $workspaceDir 'android-sdk\platform-tools\adb.exe'
  if (Test-Path -Path $localAdb2) {
    return $localAdb2
  }

  return $null
}

function Get-OnlineAdbDevices {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Adb
  )

  $lines = & $Adb devices 2>&1
  if ($LASTEXITCODE -ne 0) {
    return @()
  }

  $devices = @()
  foreach ($line in $lines) {
    if ($line -match '^\s*List of devices attached') {
      continue
    }
    if ($line -match '^\s*$') {
      continue
    }

    $cols = $line -split '\s+'
    if ($cols.Length -ge 2 -and $cols[1] -eq 'device') {
      $devices += $cols[0]
    }
  }

  return $devices
}

function Invoke-GradleTaskWithOutput {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Task
  )

  $out = & $gradlew $Task '--stacktrace' 2>&1 | Tee-Object -Variable out
  $exit = $LASTEXITCODE

  return [PSCustomObject]@{
    ExitCode = $exit
    Output = $out
  }
}

$exitCode = 0

Push-Location $projectDir
try {
  $adb = Resolve-Adb
  if (-not $adb) {
    Write-Output 'baselineprofile.ps1: SKIP（未找到 adb；请确保 ANDROID_HOME/platform-tools 在 PATH，或存在 android-sdk/platform-tools/adb.exe）'
    return
  }

  $devices = Get-OnlineAdbDevices -Adb $adb
  if (-not $devices -or $devices.Count -lt 1) {
    Write-Output 'baselineprofile.ps1: SKIP（未检测到 device 状态的设备；请执行 adb devices 确认设备已授权且为 device）'
    return
  }

  Write-Output ("baselineprofile.ps1: devices=" + ($devices -join ', '))

  $primaryTask = ':app:generateReleaseBaselineProfile'
  $fallbackTask = ':baselineprofile:connectedCheck'

  Write-Output "baselineprofile.ps1: run $primaryTask"
  $r1 = Invoke-GradleTaskWithOutput -Task $primaryTask
  if ($r1.ExitCode -ne 0) {
    $taskNotFound = $false
    foreach ($line in $r1.Output) {
      if ($line -match '(?i)Task .*generateReleaseBaselineProfile.* not found') {
        $taskNotFound = $true
        break
      }
    }

    if ($taskNotFound) {
      Write-Output "baselineprofile.ps1: primary task not found, fallback to $fallbackTask"
      $r2 = Invoke-GradleTaskWithOutput -Task $fallbackTask
      if ($r2.ExitCode -ne 0) {
        throw "Gradle 失败：$fallbackTask（exit=$($r2.ExitCode)）"
      }
    } else {
      throw "Gradle 失败：$primaryTask（exit=$($r1.ExitCode)）"
    }
  }

  Write-Output 'baselineprofile.ps1: OK'

  # Baseline Profile 最终会被复制到 app/src/main（常见文件名：baseline-prof.txt / startup-prof.txt）
  $baselineProf = Join-Path $projectDir 'app\src\main\baseline-prof.txt'
  $startupProf = Join-Path $projectDir 'app\src\main\startup-prof.txt'
  $baselineModuleOutputs = Join-Path $projectDir 'baselineprofile\build\outputs'
  $appBaselineOutputs = Join-Path $projectDir 'app\build\outputs\baselineprofile'

  Write-Output 'baselineprofile.ps1: 产物位置（请优先看 app/src/main，通常会被提交入库）：'
  Write-Output ("- " + $baselineProf)
  Write-Output ("- " + $startupProf)
  Write-Output 'baselineprofile.ps1: 中间产物/Zip（如存在）：'
  Write-Output ("- " + $baselineModuleOutputs)
  Write-Output ("- " + $appBaselineOutputs)
} catch {
  $exitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
  Write-Output "baselineprofile.ps1: FAIL (exit=$exitCode) $($_.Exception.Message)"
} finally {
  Pop-Location
}

exit $exitCode
