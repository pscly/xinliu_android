[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 以脚本所在目录为锚点，定位到 1memos 工程根目录
$projectDir = Split-Path -Parent $PSScriptRoot

$src = Join-Path $projectDir 'app/build/outputs/apk/benchmark/app-benchmark.apk'
$dstDir = Split-Path -Parent $src

if (-not (Test-Path -Path $src)) {
  throw "找不到 APK：$src"
}

$ts = Get-Date -Format 'yyyy-MM-ddTHH-mm-ss'
$dst = Join-Path $dstDir ($ts + '.apk')

Copy-Item -Force $src $dst
Write-Output $dst
