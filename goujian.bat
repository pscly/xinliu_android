@echo off
setlocal

rem Use UTF-8 code page (safe even without non-ASCII output)
chcp 65001 >nul

set "PROJECT_DIR=%~dp0"
for %%I in ("%PROJECT_DIR%..") do set "WORKSPACE_DIR=%%~fI"
pushd "%PROJECT_DIR%" >nul

rem JDK 17: if JAVA_HOME is not JDK17, try Scoop Temurin 17, then Microsoft JDK 17
set "JAVA17_OK="
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\release" (
    findstr /c:"JAVA_VERSION=\"17." "%JAVA_HOME%\release" >nul && set "JAVA17_OK=1"
  )
)

if not defined JAVA17_OK (
  set "JAVA17_SCOOP=%USERPROFILE%\scoop\apps\temurin17-jdk\current"
  if exist "%JAVA17_SCOOP%\bin\java.exe" (
    set "JAVA_HOME=%JAVA17_SCOOP%"
  ) else (
    set "JAVA17_MS=C:\Program Files\Microsoft\jdk-17.0.14.7-hotspot"
    if exist "%JAVA17_MS%\bin\java.exe" (
      set "JAVA_HOME=%JAVA17_MS%"
    )
  )
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JDK 17 not found.
  echo - Install via Scoop: scoop bucket add java ^&^& scoop install temurin17-jdk
  echo - Or set JAVA_HOME to an existing JDK 17
  popd >nul
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Android SDK: prefer workspace-local SDK (repo-root/android-sdk), otherwise env, otherwise Scoop android-clt
set "SDK_LOCAL=%WORKSPACE_DIR%\android-sdk"
if exist "%SDK_LOCAL%\cmdline-tools\latest\bin\sdkmanager.bat" (
  set "ANDROID_HOME=%SDK_LOCAL%"
  set "ANDROID_SDK_ROOT=%SDK_LOCAL%"
) else (
  if defined ANDROID_SDK_ROOT (
    set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
  ) else if not defined ANDROID_HOME (
    set "ANDROID_HOME=%USERPROFILE%\scoop\apps\android-clt\current"
    set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
  )
)

if not exist "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo ERROR: Android SDK not found at: %ANDROID_HOME%
  echo - Install via Scoop: scoop install android-clt
  echo - Then install packages:
  echo   sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
  popd >nul
  exit /b 1
)

rem Keep SDK + Gradle cache inside workspace (optional, but helps avoid polluting user profile)
set "SDK_HOME_LOCAL=%WORKSPACE_DIR%\android-sdk-home"
if exist "%SDK_HOME_LOCAL%" (
  set "ANDROID_SDK_HOME=%SDK_HOME_LOCAL%"
)

rem Ensure debug.keystore is stable (avoid INSTALL_FAILED_UPDATE_INCOMPATIBLE when updating on device)
if defined ANDROID_SDK_HOME (
  if exist "%USERPROFILE%\.android\debug.keystore" (
    if not exist "%ANDROID_SDK_HOME%\.android" mkdir "%ANDROID_SDK_HOME%\.android" >nul 2>&1
    copy /Y "%USERPROFILE%\.android\debug.keystore" "%ANDROID_SDK_HOME%\.android\debug.keystore" >nul
  )
)
set "GRADLE_HOME_LOCAL=%WORKSPACE_DIR%\.gradle-home"
if exist "%GRADLE_HOME_LOCAL%" (
  set "GRADLE_USER_HOME=%GRADLE_HOME_LOCAL%"
)

rem Ensure local.properties points to the selected SDK (typically gitignored)
set "SDK_DIR_ESC=%ANDROID_HOME:\=\\%"
set "SDK_DIR_ESC=%SDK_DIR_ESC::=\:%"
> "local.properties" echo sdk.dir=%SDK_DIR_ESC%

call "%PROJECT_DIR%gradlew.bat" :app:assembleDebug --stacktrace
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%
