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

function Invoke-GradleWithOutput {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Args
  )

  $out = & $gradlew @Args '--stacktrace' 2>&1 | Tee-Object -Variable out
  $exit = $LASTEXITCODE

  return [PSCustomObject]@{
    ExitCode = $exit
    Output = $out
  }
}

function Get-KnownResultDirs {
  $dirs = @(
    (Join-Path $projectDir 'macrobenchmark\build\outputs\connected_android_test_additional_output'),
    (Join-Path $projectDir 'macrobenchmark\build\outputs\managed_device_android_test_additional_output'),
    (Join-Path $projectDir 'macrobenchmark\build\outputs\androidTest-results')
  )

  return $dirs
}

$exitCode = 0

Push-Location $projectDir
try {
  $adb = Resolve-Adb
  if (-not $adb) {
    Write-Output 'perf-home-scroll.ps1: SKIP（未找到 adb；请确保 ANDROID_HOME/platform-tools 在 PATH，或存在 android-sdk/platform-tools/adb.exe）'
  } else {
    $devices = Get-OnlineAdbDevices -Adb $adb
    if (-not $devices -or $devices.Count -lt 1) {
      Write-Output 'perf-home-scroll.ps1: SKIP（未检测到 device 状态的设备；请执行 adb devices 确认设备已授权且为 device）'
    } else {
      Write-Output ("perf-home-scroll.ps1: devices=" + ($devices -join ', '))

      Write-Output 'perf-home-scroll.ps1: 降噪提醒（建议手动完成后再跑，避免数据抖动）：'
      Write-Output '- 关闭系统动画（开发者选项：窗口/过渡/动画时长缩放）'
      Write-Output '- 连接电源并关闭省电；避免充电电流波动（尽量同一充电器/线）'
      Write-Output '- 保持设备温度稳定（跑前冷却；避免后台发热应用）'
      Write-Output '- 清理后台/关闭无关通知；必要时飞行模式（确保不影响测试账号需求）'

      $benchmarkClass = 'cc.pscly.onememos.macrobenchmark.HomeScrollBenchmark'
      Write-Output "perf-home-scroll.ps1: run Macrobenchmark (Home scroll/jank; FrameTimingMetric) class=$benchmarkClass"
      Write-Output 'perf-home-scroll.ps1: 建议命令（等价）：'
      Write-Output "  .\\gradlew.bat :macrobenchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=$benchmarkClass --stacktrace"

      $args = @(
        ':macrobenchmark:connectedCheck',
        ("-Pandroid.testInstrumentationRunnerArguments.class=$benchmarkClass")
      )
      $r = Invoke-GradleWithOutput -Args $args
      if ($r.ExitCode -ne 0) {
        throw "Gradle 失败：:macrobenchmark:connectedCheck（exit=$($r.ExitCode)）"
      }

      Write-Output 'perf-home-scroll.ps1: OK'

      $knownDirs = Get-KnownResultDirs
      Write-Output 'perf-home-scroll.ps1: 结果位置（不同 AGP/UTP 可能略有差异，以下为常见目录）：'
      foreach ($d in $knownDirs) {
        if (Test-Path -Path $d) {
          Write-Output ("- " + $d)
        } else {
          Write-Output ("- " + $d + '（不存在）')
        }
      }

      # 可选归档：仅当 perf-results/home-scroll 已存在时才尝试复制。
      $archiveRoot = Join-Path $projectDir 'perf-results\home-scroll'
      if (Test-Path -Path $archiveRoot) {
        $timestamp = Get-Date -Format 'yyyy-MM-ddTHH-mm-ss'
        $archiveDir = Join-Path $archiveRoot $timestamp
        New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null

        $copiedAny = $false
        foreach ($d in $knownDirs) {
          if (Test-Path -Path $d) {
            $name = Split-Path -Leaf $d
            $dst = Join-Path $archiveDir $name
            Copy-Item -Recurse -Force -Path $d -Destination $dst
            $copiedAny = $true
          }
        }

        if ($copiedAny) {
          Write-Output ("perf-home-scroll.ps1: 结果已归档到：" + $archiveDir)
        } else {
          Write-Output ("perf-home-scroll.ps1: 未找到可归档的结果目录；如已生成请手动检查 macrobenchmark/build/outputs。归档目标：" + $archiveDir)
        }
      } else {
        Write-Output ("perf-home-scroll.ps1: 未检测到归档目录（跳过复制）：" + $archiveRoot)
      }
    }
  }
} catch {
  $exitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
  Write-Output "perf-home-scroll.ps1: FAIL (exit=$exitCode) $($_.Exception.Message)"
} finally {
  Pop-Location
}

exit $exitCode
