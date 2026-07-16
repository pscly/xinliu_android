[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 以脚本所在目录为锚点，定位到 1memos 工程根目录
$projectDir = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $projectDir 'gradlew.bat'
$architectureScript = Join-Path $PSScriptRoot 'verify-architecture.ps1'

if (-not (Test-Path -Path $gradlew)) {
  throw "找不到 Gradle Wrapper：$gradlew"
}
if (-not (Test-Path -Path $architectureScript)) {
  throw "找不到架构门禁脚本：$architectureScript"
}

function Invoke-GradleTask {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Task
  )

  & $gradlew $Task --stacktrace
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle 失败：$Task（exit=$LASTEXITCODE）"
  }
}

$exitCode = 0

Push-Location $projectDir
try {
  # 1) 架构边界
  & $architectureScript
  if ($LASTEXITCODE -ne 0) {
    throw "verify-architecture.ps1 失败（exit=$LASTEXITCODE）"
  }

  # 2) Debug 构建与单元测试
  Invoke-GradleTask ':app:assembleDebug'
  Invoke-GradleTask ':app:testDebugUnitTest'
  Invoke-GradleTask ':feature:settings:testDebugUnitTest'
  Invoke-GradleTask ':core:settings:testDebugUnitTest'
  Invoke-GradleTask ':core:navigation:testDebugUnitTest'
  Invoke-GradleTask ':core:update:testDebugUnitTest'
  Invoke-GradleTask ':core:calendar:testDebugUnitTest'
  Invoke-GradleTask ':core:quicktiles:testDebugUnitTest'
  Invoke-GradleTask ':core:externalactions:testDebugUnitTest'
  Invoke-GradleTask ':core:diagnostics:testDebugUnitTest'
  Invoke-GradleTask ':feature:home:testDebugUnitTest'
  Invoke-GradleTask ':feature:collections:testDebugUnitTest'
  Invoke-GradleTask ':app:lintDebug'

  # 3) Benchmark 与 profile 模块
  Invoke-GradleTask ':app:assembleBenchmark'
  Invoke-GradleTask ':baselineprofile:assembleBenchmark'
  Invoke-GradleTask ':macrobenchmark:assembleBenchmark'

  Write-Output 'verify.ps1: OK'
} catch {
  $exitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
  Write-Output "verify.ps1: FAIL (exit=$exitCode) $($_.Exception.Message)"
} finally {
  Pop-Location
}

exit $exitCode
