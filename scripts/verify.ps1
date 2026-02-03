[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 以脚本所在目录为锚点，定位到 1memos 工程根目录
$projectDir = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $projectDir 'gradlew.bat'

if (-not (Test-Path -Path $gradlew)) {
  throw "找不到 Gradle Wrapper：$gradlew"
}

function Invoke-GradleTask {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Task
  )

  & $gradlew $Task
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle 失败：$Task（exit=$LASTEXITCODE）"
  }
}

$exitCode = 0

Push-Location $projectDir
try {
  Invoke-GradleTask ':app:assembleDebug'
  Invoke-GradleTask ':app:testDebugUnitTest'
  Invoke-GradleTask ':app:lintDebug'
  Invoke-GradleTask ':baselineprofile:assemble'
  Invoke-GradleTask ':macrobenchmark:assemble'

  Write-Output 'verify.ps1: OK'
} catch {
  $exitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
  Write-Output "verify.ps1: FAIL (exit=$exitCode) $($_.Exception.Message)"
} finally {
  Pop-Location
}

exit $exitCode
