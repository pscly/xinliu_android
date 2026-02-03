[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$rootDir = $PSScriptRoot
$projectDir = Join-Path $rootDir "1memos"

if (-not (Test-Path $projectDir)) {
  throw "Project dir not found: $projectDir"
}

function Test-Java17([string]$javaHome) {
  if (-not $javaHome) { return $false }
  $javaExe = Join-Path $javaHome "bin\\java.exe"
  if (-not (Test-Path $javaExe)) { return $false }
  $out = & $javaExe -version 2>&1 | Out-String
  return $out -match 'version \"17\.' -or $out -match 'openjdk version \"17\.'
}

function Resolve-JavaHome17 {
  if (Test-Java17 $env:JAVA_HOME) { return $env:JAVA_HOME }

  $ms = "C:\Program Files\Microsoft\jdk-17.0.14.7-hotspot"
  if (Test-Java17 $ms) { return $ms }

  $scoopCandidate = Join-Path $env:USERPROFILE "scoop\\apps\\temurin17-jdk\\current"
  if (Test-Java17 $scoopCandidate) { return $scoopCandidate }

  try {
    $scoop = Get-Command scoop -ErrorAction Stop
    $prefix = & $scoop.Source prefix temurin17-jdk 2>$null
    if ($prefix) {
      $prefix = $prefix.Trim()
      if (Test-Java17 $prefix) { return $prefix }
    }
  } catch {
    # ignore
  }

  return $null
}

$javaHome = Resolve-JavaHome17
if (-not $javaHome) {
  throw "JDK 17 not found. Install via Scoop: scoop bucket add java; scoop install temurin17-jdk"
}

$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path

$sdkLocal = Join-Path $rootDir "android-sdk"
if (Test-Path (Join-Path $sdkLocal "cmdline-tools\\latest\\bin\\sdkmanager.bat")) {
  $env:ANDROID_SDK_ROOT = $sdkLocal
  $env:ANDROID_HOME = $sdkLocal
} elseif ($env:ANDROID_SDK_ROOT) {
  $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
  $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
} else {
  $env:ANDROID_HOME = Join-Path $env:USERPROFILE "scoop\\apps\\android-clt\\current"
  $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}

$sdkManager = Join-Path $env:ANDROID_HOME "cmdline-tools\\latest\\bin\\sdkmanager.bat"
if (-not (Test-Path $sdkManager)) {
  throw "Android SDK not found at: $($env:ANDROID_HOME)"
}

$sdkHomeLocal = Join-Path $rootDir "android-sdk-home"
if (Test-Path $sdkHomeLocal) {
  $env:ANDROID_SDK_HOME = $sdkHomeLocal
}

$homeDebugKeystore = Join-Path $env:USERPROFILE ".android\\debug.keystore"
if ($env:ANDROID_SDK_HOME -and (Test-Path $homeDebugKeystore)) {
  $targetAndroidDir = Join-Path $env:ANDROID_SDK_HOME ".android"
  $targetDebugKeystore = Join-Path $targetAndroidDir "debug.keystore"
  New-Item -ItemType Directory -Force -Path $targetAndroidDir | Out-Null
  Copy-Item -Force $homeDebugKeystore $targetDebugKeystore
}

$gradleHomeLocal = Join-Path $rootDir ".gradle-home"
if (Test-Path $gradleHomeLocal) {
  $env:GRADLE_USER_HOME = $gradleHomeLocal
}

$localProps = Join-Path $projectDir "local.properties"
$sdkDirForProps = ($env:ANDROID_HOME -replace "\\", "/")
Set-Content -Path $localProps -Value ("sdk.dir=" + $sdkDirForProps) -Encoding utf8

Push-Location $projectDir
try {
  & ".\\gradlew.bat" ":app:assembleDebug" "--stacktrace"
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
