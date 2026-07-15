# _env.ps1：Windows PowerShell 7 构建环境初始化
# 必须先执行 [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# 然后 dot-source 本文件：. .\scripts\_env.ps1

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$candidateHome = $env:ONE_MEMOS_JAVA_HOME
if (-not $candidateHome) { $candidateHome = $env:JAVA_HOME }
if (-not $candidateHome) { $candidateHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot" }

if (-not (Test-Path "$candidateHome\bin\java.exe")) {
    Write-Error "找不到 JAVA_HOME=$candidateHome\bin\java.exe"
    Write-Error "请安装 OpenJDK 21 或设置 ONE_MEMOS_JAVA_HOME/JAVA_HOME。"
    exit 1
}

$javaVersion = & "$candidateHome\bin\java.exe" -version 2>&1 | Select-String '"21\.'
if (-not $javaVersion) {
    Write-Error "JAVA_HOME 不是 JDK 21；本项目只接受 JDK 21。"
    exit 1
}

$env:JAVA_HOME = $candidateHome
$env:PATH = "$candidateHome\bin;$env:PATH"
