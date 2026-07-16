param(
    [string] $ApkPath,
    [string] $ExpectedVersionName,
    [int] $ExpectedVersionCode,
    [string] $AndroidHome = $env:ANDROID_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ReleasePackageName = 'cc.pscly.onememos'
$script:ReleaseBuildToolsVersion = '36.0.0'
$script:ReleaseSignerSha256 = '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'

function Invoke-ReleaseExternalCommand {
    param(
        [Parameter(Mandatory)] [string] $FilePath,
        [Parameter(Mandatory)] [string[]] $Arguments
    )

    $output = @(& $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    return [pscustomobject]@{
        exitCode = $LASTEXITCODE
        output = $output -join [Environment]::NewLine
    }
}

function Get-ReleaseBuildToolPath {
    param(
        [Parameter(Mandatory)] [string] $AndroidHome,
        [Parameter(Mandatory)] [ValidateSet('aapt2', 'apksigner')] [string] $Name
    )

    if ([string]::IsNullOrWhiteSpace($AndroidHome)) {
        throw 'ANDROID_HOME 未设置，无法定位固定 Android Build Tools 36.0.0'
    }

    $directory = Join-Path $AndroidHome "build-tools/$script:ReleaseBuildToolsVersion"
    $candidates = if ($IsWindows) {
        if ($Name -eq 'aapt2') { @('aapt2.exe') } else { @('apksigner.bat', 'apksigner') }
    } else {
        @($Name)
    }
    foreach ($candidate in $candidates) {
        $path = Join-Path $directory $candidate
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            return [System.IO.Path]::GetFullPath($path)
        }
    }
    throw "固定 Build Tools 缺少 $Name：$directory"
}

function Get-ReleaseBadgingValue {
    param(
        [Parameter(Mandatory)] [string] $Badging,
        [Parameter(Mandatory)] [string] $Name
    )

    $match = [regex]::Match($Badging, "(?m)^package:.*\b$([regex]::Escape($Name))='([^']*)'")
    if (-not $match.Success) {
        throw "APK badging 缺少 $Name"
    }
    return $match.Groups[1].Value
}

function Invoke-ReleaseApkVerification {
    param(
        [Parameter(Mandatory)] [string] $ApkPath,
        [Parameter(Mandatory)] [string] $ExpectedVersionName,
        [Parameter(Mandatory)] [int] $ExpectedVersionCode,
        [string] $AndroidHome = $env:ANDROID_HOME,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseExternalCommand}
    )

    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
        throw "找不到待核验 APK：$ApkPath"
    }
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    if ([System.IO.Path]::GetFileName($resolvedApk) -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.apk$') {
        throw "APK 文件名不是时间戳格式：$resolvedApk"
    }

    $aapt2 = Get-ReleaseBuildToolPath -AndroidHome $AndroidHome -Name 'aapt2'
    $apksigner = Get-ReleaseBuildToolPath -AndroidHome $AndroidHome -Name 'apksigner'

    $badgingResult = & $CommandRunner -FilePath $aapt2 -Arguments @('dump', 'badging', $resolvedApk)
    if ([int] $badgingResult.exitCode -ne 0) {
        throw "aapt2 dump badging 失败：$($badgingResult.output)"
    }
    $packageName = Get-ReleaseBadgingValue -Badging ([string] $badgingResult.output) -Name 'name'
    $versionName = Get-ReleaseBadgingValue -Badging ([string] $badgingResult.output) -Name 'versionName'
    $versionCodeText = Get-ReleaseBadgingValue -Badging ([string] $badgingResult.output) -Name 'versionCode'
    if ($packageName -cne $script:ReleasePackageName) {
        throw "APK 包名漂移：expected=$script:ReleasePackageName actual=$packageName"
    }
    if ($versionName -cne $ExpectedVersionName) {
        throw "APK versionName 漂移：expected=$ExpectedVersionName actual=$versionName"
    }
    $parsedVersionCode = 0
    if (-not [int]::TryParse($versionCodeText, [ref] $parsedVersionCode) -or $parsedVersionCode -ne $ExpectedVersionCode) {
        throw "APK versionCode 漂移：expected=$ExpectedVersionCode actual=$versionCodeText"
    }

    $signatureResult = & $CommandRunner -FilePath $apksigner -Arguments @('verify', '--verbose', '--print-certs', $resolvedApk)
    if ([int] $signatureResult.exitCode -ne 0) {
        throw "apksigner verify 失败：$($signatureResult.output)"
    }
    $certificateMatches = [regex]::Matches(
        [string] $signatureResult.output,
        '(?im)certificate SHA-256 digest:\s*([0-9a-f:]{64,95})'
    )
    $certificateDigests = @(
        $certificateMatches |
            ForEach-Object { $_.Groups[1].Value.Replace(':', '').ToLowerInvariant() } |
            Sort-Object -Unique
    )
    if ($certificateDigests.Count -ne 1) {
        throw "APK 必须恰有一个签名证书，实际为 $($certificateDigests.Count)"
    }
    if ($certificateDigests[0] -cne $script:ReleaseSignerSha256) {
        throw "APK 签名证书漂移：expected=$script:ReleaseSignerSha256 actual=$($certificateDigests[0])"
    }

    return [pscustomobject]@{
        localPath = $resolvedApk
        fileName = [System.IO.Path]::GetFileName($resolvedApk)
        packageName = $packageName
        versionName = $versionName
        versionCode = $parsedVersionCode
        sha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
        signerSha256 = $certificateDigests[0]
        buildToolsVersion = $script:ReleaseBuildToolsVersion
        verifiedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($ApkPath) -or [string]::IsNullOrWhiteSpace($ExpectedVersionName) -or $ExpectedVersionCode -le 0) {
        throw '必须提供 -ApkPath、-ExpectedVersionName 与正整数 -ExpectedVersionCode'
    }
    Invoke-ReleaseApkVerification `
        -ApkPath $ApkPath `
        -ExpectedVersionName $ExpectedVersionName `
        -ExpectedVersionCode $ExpectedVersionCode `
        -AndroidHome $AndroidHome |
        ConvertTo-Json -Depth 16
}
