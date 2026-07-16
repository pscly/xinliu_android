param(
    [ValidateSet('Prepare', 'PrepareRecovery')]
    [string] $Stage = 'Prepare',
    [string] $OutputPath,
    [string] $CandidateSha,
    [string] $ExpectedOldTagObjectId,
    [string] $ExpectedOldTagPeeledSha,
    [string] $RecoveryEvidencePath,
    [switch] $FinalizeWalkthrough,
    [string] $ConfirmWalkthrough
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Get-Command -Name Get-ReleaseVersionFromGradleText -CommandType Function -ErrorAction SilentlyContinue)) {
    $requestedParameters = @{
        Stage = $Stage
        OutputPath = $OutputPath
        CandidateSha = $CandidateSha
        ExpectedOldTagObjectId = $ExpectedOldTagObjectId
        ExpectedOldTagPeeledSha = $ExpectedOldTagPeeledSha
        RecoveryEvidencePath = $RecoveryEvidencePath
        FinalizeWalkthrough = $FinalizeWalkthrough
        ConfirmWalkthrough = $ConfirmWalkthrough
    }
    . (Join-Path $PSScriptRoot 'release-publish.ps1') -Stage Status
    foreach ($entry in $requestedParameters.GetEnumerator()) {
        Set-Variable -Name $entry.Key -Value $entry.Value -Scope Script
    }
}

function Get-ReleaseSigningStatus {
    param(
        [hashtable] $Values = @{
            ANDROID_RELEASE_KEYSTORE_PATH = $env:ANDROID_RELEASE_KEYSTORE_PATH
            ANDROID_RELEASE_STORE_PASSWORD = $env:ANDROID_RELEASE_STORE_PASSWORD
            ANDROID_RELEASE_KEY_ALIAS = $env:ANDROID_RELEASE_KEY_ALIAS
            ANDROID_RELEASE_KEY_PASSWORD = $env:ANDROID_RELEASE_KEY_PASSWORD
        }
    )

    $required = @(
        'ANDROID_RELEASE_KEYSTORE_PATH',
        'ANDROID_RELEASE_STORE_PASSWORD',
        'ANDROID_RELEASE_KEY_ALIAS',
        'ANDROID_RELEASE_KEY_PASSWORD'
    )
    $missing = @(
        $required | Where-Object {
            -not $Values.ContainsKey($_) -or
            [string]::IsNullOrWhiteSpace([string] $Values[$_])
        }
    )
    return [pscustomobject]@{
        available = $missing.Count -eq 0
        reasonCode = if ($missing.Count -eq 0) { 'SIGNING_AVAILABLE' } else { 'BLOCKED_SIGNING_MISSING' }
        missing = $missing
    }
}

function Set-ReleaseVersionInGradleText {
    param(
        [Parameter(Mandatory)] [string] $Text,
        [Parameter(Mandatory)] [string] $CurrentVersionName,
        [Parameter(Mandatory)] [int] $CurrentVersionCode,
        [Parameter(Mandatory)] [string] $TargetVersionName,
        [Parameter(Mandatory)] [int] $TargetVersionCode
    )

    ConvertTo-ReleaseVersion -Value $TargetVersionName | Out-Null
    if ($TargetVersionCode -le 0) {
        throw '目标 versionCode 必须为正整数'
    }
    $current = Get-ReleaseVersionFromGradleText -Text $Text -Source 'app/build.gradle.kts'
    if ($current.versionName -cne $CurrentVersionName -or $current.versionCode -ne $CurrentVersionCode) {
        throw "版本写入前现场漂移：expected=$CurrentVersionName ($CurrentVersionCode) actual=$($current.versionName) ($($current.versionCode))"
    }
    if ($current.versionName -ceq $TargetVersionName -and $current.versionCode -eq $TargetVersionCode) {
        return $Text
    }

    $namePattern = [regex]::new('(?m)^(\s*versionName\s*=\s*")[^"]+("\s*)$')
    $codePattern = [regex]::new('(?m)^(\s*versionCode\s*=\s*)[0-9]+(\s*)$')
    $updated = $namePattern.Replace(
        $Text,
        [System.Text.RegularExpressions.MatchEvaluator] {
            param($match)
            return "$($match.Groups[1].Value)$TargetVersionName$($match.Groups[2].Value)"
        }
    )
    return $codePattern.Replace(
        $updated,
        [System.Text.RegularExpressions.MatchEvaluator] {
            param($match)
            return "$($match.Groups[1].Value)$TargetVersionCode$($match.Groups[2].Value)"
        }
    )
}

function Get-ReleaseGateStepDefinitions {
    return @(
        [pscustomobject]@{ id = 'verifyArchitecture'; kind = 'architecture'; task = $null },
        [pscustomobject]@{ id = 'testDebugUnitTest'; kind = 'gradle'; task = 'testDebugUnitTest' },
        [pscustomobject]@{ id = 'lint'; kind = 'gradle'; task = 'lint' },
        [pscustomobject]@{ id = 'assembleAppBenchmark'; kind = 'gradle'; task = ':app:assembleBenchmark' },
        [pscustomobject]@{ id = 'assembleBaselineProfileBenchmark'; kind = 'gradle'; task = ':baselineprofile:assembleBenchmark' },
        [pscustomobject]@{ id = 'assembleMacrobenchmarkBenchmark'; kind = 'gradle'; task = ':macrobenchmark:assembleBenchmark' },
        [pscustomobject]@{ id = 'verifyApk'; kind = 'apk' },
        [pscustomobject]@{ id = 'deviceReleaseExtensions'; kind = 'device' }
    )
}

function Write-ReleaseTextAtomic {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Text
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $directory = [System.IO.Path]::GetDirectoryName($fullPath)
    if ([string]::IsNullOrWhiteSpace($directory)) {
        throw "文本输出路径缺少父目录：$Path"
    }
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $temporaryPath = Join-Path $directory ".$([System.IO.Path]::GetFileName($fullPath)).$([guid]::NewGuid()).tmp"
    try {
        [System.IO.File]::WriteAllText(
            $temporaryPath,
            $Text,
            [System.Text.UTF8Encoding]::new($false)
        )
        [System.IO.File]::Move($temporaryPath, $fullPath, $true)
    } finally {
        Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-ReleaseGateCommand {
    param(
        [Parameter(Mandatory)] [string] $FilePath,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Description,
        [Parameter(Mandatory)] [scriptblock] $CommandRunner
    )

    $result = & $CommandRunner -FilePath $FilePath -Arguments $Arguments
    if ([int] $result.exitCode -ne 0) {
        throw "$Description 失败：$($result.output)"
    }
    return $result
}

function Copy-ReleaseBenchmarkApk {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [scriptblock] $Clock = { Get-Date }
    )

    $source = Join-Path $ProjectDir 'app/build/outputs/apk/benchmark/app-benchmark.apk'
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "找不到 Benchmark APK：$source"
    }
    $timestamp = (& $Clock).ToString('yyyy-MM-ddTHH-mm-ss')
    $destination = Join-Path ([System.IO.Path]::GetDirectoryName($source)) "$timestamp.apk"
    Copy-Item -LiteralPath $source -Destination $destination -Force
    return [System.IO.Path]::GetFullPath($destination)
}

function Get-ReleaseAdbDevices {
    param([Parameter(Mandatory)] [scriptblock] $CommandRunner)

    $result = & $CommandRunner -FilePath 'adb' -Arguments @('devices')
    if ([int] $result.exitCode -ne 0) {
        throw "adb devices 失败：$($result.output)"
    }
    return @(
        [string] $result.output -split "`r?`n" |
            Where-Object { $_ -cmatch '^([^\s]+)\s+device$' } |
            ForEach-Object { ([regex]::Match($_, '^([^\s]+)')).Groups[1].Value }
    )
}

function Invoke-DefaultReleaseGateStep {
    param(
        [Parameter(Mandatory)] [object] $Definition,
        [Parameter(Mandatory)] [object] $Context
    )

    $projectDir = [string] $Context.projectDir
    $runner = [scriptblock] $Context.commandRunner
    $id = [string] $Definition.id
    $startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $artifact = $null
    $status = 'PASSED'

    switch ([string] $Definition.kind) {
        'architecture' {
            if ($IsWindows) {
                Invoke-ReleaseGateCommand `
                    -FilePath (Join-Path $PSHOME 'pwsh.exe') `
                    -Arguments @(
                        '-NoProfile', '-NonInteractive', '-File',
                        (Join-Path $projectDir 'scripts/verify-architecture.ps1')
                    ) `
                    -Description '架构门禁' `
                    -CommandRunner $runner | Out-Null
            } else {
                Invoke-ReleaseGateCommand `
                    -FilePath 'bash' `
                    -Arguments @((Join-Path $projectDir 'scripts/verify-architecture.sh')) `
                    -Description '架构门禁' `
                    -CommandRunner $runner | Out-Null
            }
        }
        'gradle' {
            $gradlew = Join-Path $projectDir (if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
            Invoke-ReleaseGateCommand `
                -FilePath $gradlew `
                -Arguments @(
                    [string] $Definition.task,
                    '-Pkotlin.compiler.execution.strategy=in-process',
                    '--stacktrace'
                ) `
                -Description "Gradle 门禁 $($Definition.task)" `
                -CommandRunner $runner | Out-Null
        }
        'apk' {
            $apkPath = Copy-ReleaseBenchmarkApk -ProjectDir $projectDir
            $artifact = Invoke-ReleaseApkVerification `
                -ApkPath $apkPath `
                -ExpectedVersionName ([string] $Context.targetVersion) `
                -ExpectedVersionCode ([int] $Context.targetVersionCode) `
                -AndroidHome (Get-ReleaseAndroidHome -ProjectDir $projectDir) `
                -CommandRunner $runner
        }
        'device' {
            $devices = @(Get-ReleaseAdbDevices -CommandRunner $runner)
            if ($devices.Count -eq 0) {
                $status = 'SKIPPED_NO_DEVICE'
            } else {
                $serial = [string] $env:ADB_SERIAL
                if ([string]::IsNullOrWhiteSpace($serial)) {
                    if ($devices.Count -ne 1) {
                        throw '检测到多台设备，必须通过 ADB_SERIAL 指定发布核验设备'
                    }
                    $serial = $devices[0]
                }
                if ($serial -notin $devices) {
                    throw "ADB_SERIAL 不在已连接设备中：$serial"
                }
                Invoke-ReleaseGateCommand `
                    -FilePath 'adb' `
                    -Arguments @('-s', $serial, 'install', '-r', '-d', '-g', [string] $Context.artifact.localPath) `
                    -Description '安装发布 Benchmark APK' `
                    -CommandRunner $runner | Out-Null
                $package = Invoke-ReleaseGateCommand `
                    -FilePath 'adb' `
                    -Arguments @('-s', $serial, 'shell', 'dumpsys', 'package', 'cc.pscly.onememos') `
                    -Description '核验设备安装版本' `
                    -CommandRunner $runner
                $versionName = [regex]::Match([string] $package.output, '(?m)\bversionName=([^\s]+)').Groups[1].Value
                $versionCode = [regex]::Match([string] $package.output, '(?m)\bversionCode=([0-9]+)').Groups[1].Value
                if ($versionName -cne [string] $Context.targetVersion -or
                    $versionCode -cne [string] $Context.targetVersionCode) {
                    throw "设备安装版本不匹配：$versionName ($versionCode)"
                }
                $status = 'PASSED_DEVICE_INSTALL'
            }
        }
        default {
            throw "未知门禁步骤类型：$($Definition.kind)"
        }
    }

    return [pscustomobject][ordered]@{
        id = $id
        exitCode = 0
        status = $status
        startedAt = $startedAt
        completedAt = [DateTimeOffset]::UtcNow.ToString('o')
        artifact = $artifact
    }
}

function New-ReleasePrepareResult {
    param(
        [Parameter(Mandatory)] [object] $StateResult,
        [Parameter(Mandatory)] [string] $Stage,
        [Parameter(Mandatory)] [bool] $Ok,
        [Parameter(Mandatory)] [string] $ReasonCode,
        [Parameter(Mandatory)] [string] $Message,
        [object] $Artifact = ([pscustomobject]@{}),
        [object] $Gate = ([pscustomobject]@{ steps = @() })
    )

    return [pscustomobject][ordered]@{
        schemaVersion = 1
        ok = $Ok
        stage = $Stage
        state = [string] $StateResult.state
        reasonCode = $ReasonCode
        message = $Message
        targetVersion = Get-ReleaseProperty $StateResult 'targetVersion'
        targetVersionCode = [int] (Get-ReleaseProperty $StateResult 'targetVersionCode' 0)
        targetTag = Get-ReleaseProperty $StateResult 'targetTag'
        targetSha = Get-ReleaseProperty $StateResult 'targetSha'
        baselineTag = [string] (Get-ReleaseProperty $StateResult 'baselineTag' '')
        cacheStatus = [string] (Get-ReleaseProperty $StateResult 'cacheStatus' 'Missing')
        tag = Get-ReleaseProperty $StateResult 'tag' ([pscustomobject]@{})
        run = Get-ReleaseProperty $StateResult 'run' ([pscustomobject]@{})
        artifact = $Artifact
        release = Get-ReleaseProperty $StateResult 'release' ([pscustomobject]@{})
        recovery = Get-ReleaseProperty $StateResult 'recovery' ([pscustomobject]@{})
        transition = [string] (Get-ReleaseProperty $StateResult 'transition' 'Stop')
        gate = $Gate
        observedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
}

function Get-ReleaseRecoverySnapshot {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [object] $StateResult,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    Push-Location $ProjectDir
    try {
        Invoke-ReleaseCheckedCommand `
            -FilePath 'git' `
            -Arguments @('fetch', '--prune', 'origin', 'refs/heads/main:refs/remotes/origin/main') `
            -Description '刷新恢复门禁 origin/main' `
            -CommandRunner $CommandRunner | Out-Null
        $branch = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('branch', '--show-current') -Description '读取恢复分支' -CommandRunner $CommandRunner).Trim()
        $headSha = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('rev-parse', 'HEAD') -Description '读取恢复 HEAD' -CommandRunner $CommandRunner).Trim()
        $originMainSha = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('rev-parse', 'origin/main') -Description '读取恢复 origin/main' -CommandRunner $CommandRunner).Trim()
        $dirtyPaths = @(Get-ReleaseDirtyPaths -CommandRunner $CommandRunner)
        $indexResult = & $CommandRunner -FilePath 'git' -Arguments @('diff', '--cached', '--quiet', '--exit-code')
        if ([int] $indexResult.exitCode -notin @(0, 1)) {
            throw "读取恢复索引状态失败：$($indexResult.output)"
        }
        $remoteTags = Get-ReleaseRemoteTags -CommandRunner $CommandRunner
        $targetTag = [string] $StateResult.targetTag
        $tag = if ($remoteTags.ContainsKey($targetTag)) { $remoteTags[$targetTag] } else { $null }
        return [pscustomobject]@{
            branch = $branch
            headSha = $headSha
            worktreeClean = $dirtyPaths.Count -eq 0
            indexClean = [int] $indexResult.exitCode -eq 0
            originMainSha = $originMainSha
            tagObjectId = if ($null -eq $tag) { $null } else { [string] $tag.objectId }
            tagPeeledSha = if ($null -eq $tag) { $null } else { [string] $tag.peeledSha }
        }
    } finally {
        Pop-Location
    }
}

function Assert-ReleaseRecoverySnapshot {
    param(
        [Parameter(Mandatory)] [object] $Snapshot,
        [Parameter(Mandatory)] [string] $CandidateSha,
        [Parameter(Mandatory)] [string] $OldTagObjectId,
        [Parameter(Mandatory)] [string] $OldTagPeeledSha,
        [Parameter(Mandatory)] [string] $PrePushOriginMainSha
    )

    if ((Get-ReleaseProperty $Snapshot 'branch') -cne 'main' -or
        (Get-ReleaseProperty $Snapshot 'headSha') -cne $CandidateSha -or
        (Get-ReleaseProperty $Snapshot 'worktreeClean' $false) -ne $true -or
        (Get-ReleaseProperty $Snapshot 'indexClean' $false) -ne $true -or
        (Get-ReleaseProperty $Snapshot 'originMainSha') -cne $PrePushOriginMainSha -or
        (Get-ReleaseProperty $Snapshot 'tagObjectId') -cne $OldTagObjectId -or
        (Get-ReleaseProperty $Snapshot 'tagPeeledSha') -cne $OldTagPeeledSha) {
        throw '恢复门禁现场与候选 B、旧 Tag O/A 或推送前 origin/main 不一致'
    }
}

function ConvertTo-ReleaseRecoveryRepositoryEvidence {
    param(
        [Parameter(Mandatory)] [object] $Before,
        [Parameter(Mandatory)] [object] $After
    )

    return [pscustomobject][ordered]@{
        branchBefore = [string] $Before.branch
        headBefore = [string] $Before.headSha
        worktreeCleanBefore = [bool] $Before.worktreeClean
        indexCleanBefore = [bool] $Before.indexClean
        originMainBefore = [string] $Before.originMainSha
        tagObjectBefore = [string] $Before.tagObjectId
        tagPeeledBefore = [string] $Before.tagPeeledSha
        branchAfter = [string] $After.branch
        headAfter = [string] $After.headSha
        worktreeCleanAfter = [bool] $After.worktreeClean
        indexCleanAfter = [bool] $After.indexClean
        originMainAfter = [string] $After.originMainSha
        tagObjectAfter = [string] $After.tagObjectId
        tagPeeledAfter = [string] $After.tagPeeledSha
    }
}

function New-ReleasePendingRecoveryEvidence {
    param(
        [Parameter(Mandatory)] [object] $StateResult,
        [Parameter(Mandatory)] [object] $Before,
        [Parameter(Mandatory)] [object] $After,
        [Parameter(Mandatory)] [object[]] $Steps,
        [Parameter(Mandatory)] [object] $Artifact,
        [Parameter(Mandatory)] [string] $CandidateSha,
        [Parameter(Mandatory)] [string] $OldTagObjectId,
        [Parameter(Mandatory)] [string] $OldTagPeeledSha,
        [Parameter(Mandatory)] [string] $PrePushOriginMainSha,
        [Parameter(Mandatory)] [string] $StartedAt
    )

    $run = Get-ReleaseProperty $StateResult 'run' ([pscustomobject]@{})
    $evidence = [pscustomobject][ordered]@{
        schemaVersion = 1
        kind = 'same-version-tag-recovery-local-gate'
        targetTag = [string] $StateResult.targetTag
        targetVersion = [string] $StateResult.targetVersion
        targetVersionCode = [int] $StateResult.targetVersionCode
        candidateSha = $CandidateSha
        oldTagObjectId = $OldTagObjectId
        oldTagPeeledSha = $OldTagPeeledSha
        prePushOriginMainSha = $PrePushOriginMainSha
        repository = ConvertTo-ReleaseRecoveryRepositoryEvidence -Before $Before -After $After
        failedRun = [pscustomobject][ordered]@{
            workflow = Get-ReleaseProperty $run 'workflow'
            event = Get-ReleaseProperty $run 'event'
            headBranch = Get-ReleaseProperty $run 'headBranch'
            headSha = Get-ReleaseProperty $run 'headSha'
            databaseId = [int64] (Get-ReleaseProperty $run 'databaseId' 0)
            attempt = [int] (Get-ReleaseProperty $run 'attempt' 0)
            status = Get-ReleaseProperty $run 'status'
            conclusion = Get-ReleaseProperty $run 'conclusion'
            completedAt = Get-ReleaseProperty $run 'completedAt'
        }
        gate = [pscustomobject][ordered]@{
            profile = 'full-release'
            startedAt = $StartedAt
            completedAt = [DateTimeOffset]::UtcNow.ToString('o')
            steps = @($Steps)
        }
        artifact = $Artifact
        walkthrough = [pscustomobject][ordered]@{
            checklistId = 'task35-release-walkthrough-v1'
            candidateSha = $CandidateSha
            apkSha256 = [string] $Artifact.sha256
            completed = $false
            confirmedAt = $null
        }
        createdAt = $null
        payloadSha256 = $null
    }
    $evidence.payloadSha256 = Get-RecoveryPayloadSha256 -Evidence $evidence
    return $evidence
}

function Assert-ReleasePendingRecoveryEvidence {
    param(
        [Parameter(Mandatory)] [object] $Evidence,
        [Parameter(Mandatory)] [object] $StateResult,
        [Parameter(Mandatory)] [object] $Snapshot,
        [Parameter(Mandatory)] [string] $CandidateSha,
        [Parameter(Mandatory)] [string] $OldTagObjectId,
        [Parameter(Mandatory)] [string] $OldTagPeeledSha
    )

    $prePushOriginMainSha = [string] (Get-ReleaseProperty $Evidence 'prePushOriginMainSha')
    Assert-ReleaseRecoverySnapshot `
        -Snapshot $Snapshot `
        -CandidateSha $CandidateSha `
        -OldTagObjectId $OldTagObjectId `
        -OldTagPeeledSha $OldTagPeeledSha `
        -PrePushOriginMainSha $prePushOriginMainSha
    $pendingChecks = @(
        @('SCHEMA_VERSION', ([int] (Get-ReleaseProperty $Evidence 'schemaVersion' 0) -eq 1)),
        @('KIND', ((Get-ReleaseProperty $Evidence 'kind') -ceq 'same-version-tag-recovery-local-gate')),
        @('TARGET_TAG', ((Get-ReleaseProperty $Evidence 'targetTag') -ceq (Get-ReleaseProperty $StateResult 'targetTag'))),
        @('TARGET_VERSION', ((Get-ReleaseProperty $Evidence 'targetVersion') -ceq (Get-ReleaseProperty $StateResult 'targetVersion'))),
        @('TARGET_VERSION_CODE', ([int] (Get-ReleaseProperty $Evidence 'targetVersionCode' 0) -eq [int] (Get-ReleaseProperty $StateResult 'targetVersionCode'))),
        @('CANDIDATE_SHA', ((Get-ReleaseProperty $Evidence 'candidateSha') -ceq $CandidateSha)),
        @('OLD_TAG_OBJECT_ID', ((Get-ReleaseProperty $Evidence 'oldTagObjectId') -ceq $OldTagObjectId)),
        @('OLD_TAG_PEELED_SHA', ((Get-ReleaseProperty $Evidence 'oldTagPeeledSha') -ceq $OldTagPeeledSha)),
        @('WALKTHROUGH_PENDING', ((Get-ReleaseProperty (Get-ReleaseProperty $Evidence 'walkthrough') 'completed' $true) -eq $false)),
        @('PAYLOAD_SHA256', ((Get-ReleaseProperty $Evidence 'payloadSha256') -ceq (Get-RecoveryPayloadSha256 -Evidence $Evidence)))
    )
    foreach ($check in $pendingChecks) {
        if (-not [bool] $check[1]) {
            throw "恢复门禁 .pending 证据无效或已漂移：$($check[0])"
        }
    }
    $steps = @(Get-ReleaseProperty (Get-ReleaseProperty $Evidence 'gate') 'steps' @())
    $ids = @($steps | ForEach-Object { [string] (Get-ReleaseProperty $_ 'id') })
    if ($ids.Count -ne $script:ReleaseRequiredRecoverySteps.Count -or
        (Compare-Object ($ids | Sort-Object) ($script:ReleaseRequiredRecoverySteps | Sort-Object)) -or
        @($steps | Where-Object { [int] (Get-ReleaseProperty $_ 'exitCode' -1) -ne 0 }).Count -ne 0) {
        throw '恢复门禁 .pending 缺少完整成功步骤'
    }
    $artifact = Get-ReleaseProperty $Evidence 'artifact'
    if ((Get-ReleaseProperty $artifact 'versionName') -cne (Get-ReleaseProperty $StateResult 'targetVersion') -or
        [int] (Get-ReleaseProperty $artifact 'versionCode' 0) -ne [int] (Get-ReleaseProperty $StateResult 'targetVersionCode') -or
        (Get-ReleaseProperty $artifact 'signerSha256') -cne $script:ReleaseSignerSha256 -or
        (Get-ReleaseProperty $artifact 'sha256') -cnotmatch '^[0-9a-f]{64}$') {
        throw '恢复门禁 .pending APK 证据无效'
    }
}

function Invoke-ReleasePreparation {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('Prepare', 'PrepareRecovery')]
        [string] $Stage,
        [Parameter(Mandatory)] [string] $ProjectDir,
        [hashtable] $SigningValues = @{
            ANDROID_RELEASE_KEYSTORE_PATH = $env:ANDROID_RELEASE_KEYSTORE_PATH
            ANDROID_RELEASE_STORE_PASSWORD = $env:ANDROID_RELEASE_STORE_PASSWORD
            ANDROID_RELEASE_KEY_ALIAS = $env:ANDROID_RELEASE_KEY_ALIAS
            ANDROID_RELEASE_KEY_PASSWORD = $env:ANDROID_RELEASE_KEY_PASSWORD
        },
        [scriptblock] $StateResolver = {
            param([string] $ProjectDir)
            Get-ResolvedReleaseState -ProjectDir $ProjectDir
        },
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand},
        [scriptblock] $GateStepRunner = ${function:Invoke-DefaultReleaseGateStep},
        [scriptblock] $RecoverySnapshotResolver = {
            param([string] $ProjectDir, [object] $StateResult)
            Get-ReleaseRecoverySnapshot -ProjectDir $ProjectDir -StateResult $StateResult
        },
        [string] $CandidateSha,
        [string] $ExpectedOldTagObjectId,
        [string] $ExpectedOldTagPeeledSha,
        [string] $RecoveryEvidencePath,
        [switch] $FinalizeWalkthrough,
        [string] $ConfirmWalkthrough
    )

    $stateResult = & $StateResolver -ProjectDir $ProjectDir
    $signing = Get-ReleaseSigningStatus -Values $SigningValues
    if (-not $signing.available) {
        return New-ReleasePrepareResult `
            -StateResult $stateResult `
            -Stage $Stage `
            -Ok $false `
            -ReasonCode ([string] $signing.reasonCode) `
            -Message "缺少固定发布签名配置：$($signing.missing -join ',')"
    }

    if ($Stage -eq 'Prepare' -and $stateResult.state -notin @('NewRelease', 'PreparedWorktree', 'Prepared')) {
        if ($stateResult.state -in @('RecoveryCandidate', 'RecoveryPrepared', 'TagRecoveryRequired')) {
            throw 'USE_PREPARE_RECOVERY'
        }
        throw "Prepare 只接受 NewRelease、PreparedWorktree 或 Prepared，实际为 $($stateResult.state)"
    }

    if ($Stage -eq 'PrepareRecovery') {
        if ($stateResult.state -eq 'RecoveryPrepared' -and
            (Test-Path -LiteralPath $RecoveryEvidencePath -PathType Leaf)) {
            return New-ReleasePrepareResult `
                -StateResult $stateResult `
                -Stage $Stage `
                -Ok $true `
                -ReasonCode 'RECOVERY_GATE_VALID' `
                -Message '同版本恢复门禁证据已存在且有效'
        }
        if ($stateResult.state -ne 'RecoveryCandidate') {
            throw "PrepareRecovery 只接受 RecoveryCandidate，实际为 $($stateResult.state)"
        }
        foreach ($sha in @($CandidateSha, $ExpectedOldTagObjectId, $ExpectedOldTagPeeledSha)) {
            if (-not (Test-FullGitSha $sha)) { throw "PrepareRecovery 参数含无效 Git SHA：$sha" }
        }
        $recovery = Get-ReleaseProperty $stateResult 'recovery' ([pscustomobject]@{})
        if ($CandidateSha -cne (Get-ReleaseProperty $recovery 'candidateSha') -or
            $ExpectedOldTagObjectId -cne (Get-ReleaseProperty $recovery 'oldTagObjectId') -or
            $ExpectedOldTagPeeledSha -cne (Get-ReleaseProperty $recovery 'oldTagPeeledSha') -or
            [string]::IsNullOrWhiteSpace($RecoveryEvidencePath) -or
            [System.IO.Path]::GetFullPath($RecoveryEvidencePath) -cne
                [System.IO.Path]::GetFullPath([string] (Get-ReleaseProperty $recovery 'gateEvidencePath'))) {
            throw 'PrepareRecovery 参数与现场恢复事实不一致'
        }
        if (Test-Path -LiteralPath $RecoveryEvidencePath -PathType Leaf) {
            throw '最终恢复门禁证据已存在，禁止覆盖'
        }

        $pendingPath = "$RecoveryEvidencePath.pending"
        if ($FinalizeWalkthrough) {
            if (-not (Test-Path -LiteralPath $pendingPath -PathType Leaf)) {
                throw '缺少恢复门禁 .pending 证据，不能完成人工走查'
            }
            $pending = Get-Content -LiteralPath $pendingPath -Raw -Encoding utf8 | ConvertFrom-Json -Depth 100
            $snapshot = & $RecoverySnapshotResolver -ProjectDir $ProjectDir -StateResult $stateResult
            Assert-ReleasePendingRecoveryEvidence `
                -Evidence $pending `
                -StateResult $stateResult `
                -Snapshot $snapshot `
                -CandidateSha $CandidateSha `
                -OldTagObjectId $ExpectedOldTagObjectId `
                -OldTagPeeledSha $ExpectedOldTagPeeledSha
            $artifact = Get-ReleaseProperty $pending 'artifact'
            $expectedConfirmation = "RECOVERY_WALKTHROUGH:$CandidateSha`:$($artifact.sha256)"
            if ($ConfirmWalkthrough -cne $expectedConfirmation) {
                throw '人工走查确认串未精确绑定恢复候选与 APK SHA-256'
            }
            $pending.walkthrough.completed = $true
            $pending.walkthrough.confirmedAt = [DateTimeOffset]::UtcNow.ToString('o')
            $pending.createdAt = [DateTimeOffset]::UtcNow.ToString('o')
            $pending.payloadSha256 = Get-RecoveryPayloadSha256 -Evidence $pending
            $expected = [pscustomobject]@{
                targetTag = [string] $stateResult.targetTag
                targetVersion = [string] $stateResult.targetVersion
                targetVersionCode = [int] $stateResult.targetVersionCode
                candidateSha = $CandidateSha
                oldTagObjectId = $ExpectedOldTagObjectId
                oldTagPeeledSha = $ExpectedOldTagPeeledSha
                prePushOriginMainSha = [string] $pending.prePushOriginMainSha
                signerSha256 = $script:ReleaseSignerSha256
            }
            $validation = Test-RecoveryEvidence -Evidence $pending -Expected $expected
            if ($validation.status -ne 'Valid') {
                throw "最终恢复门禁证据无效：$($validation.reasonCode)"
            }
            Write-ReleaseJsonAtomic -Path $RecoveryEvidencePath -Value $pending
            Remove-Item -LiteralPath $pendingPath -Force
            $resolved = & $StateResolver -ProjectDir $ProjectDir
            if ($resolved.state -ne 'RecoveryPrepared') {
                throw "最终证据写入后预期 RecoveryPrepared，实际为 $($resolved.state)"
            }
            return New-ReleasePrepareResult `
                -StateResult $resolved `
                -Stage $Stage `
                -Ok $true `
                -ReasonCode 'RECOVERY_GATE_VALID' `
                -Message '同版本恢复门禁与人工走查证据已完成' `
                -Artifact $artifact `
                -Gate (Get-ReleaseProperty $pending 'gate')
        }

        $before = & $RecoverySnapshotResolver -ProjectDir $ProjectDir -StateResult $stateResult
        $prePushOriginMainSha = [string] (Get-ReleaseProperty $before 'originMainSha')
        Assert-ReleaseRecoverySnapshot `
            -Snapshot $before `
            -CandidateSha $CandidateSha `
            -OldTagObjectId $ExpectedOldTagObjectId `
            -OldTagPeeledSha $ExpectedOldTagPeeledSha `
            -PrePushOriginMainSha $prePushOriginMainSha
        if ($prePushOriginMainSha -ceq $CandidateSha) {
            throw 'PrepareRecovery 必须在恢复候选推送 main 前生成门禁证据'
        }
    }

    $versionPath = Join-Path $ProjectDir 'app/build.gradle.kts'
    if ($stateResult.state -eq 'NewRelease') {
        $text = [System.IO.File]::ReadAllText($versionPath)
        $current = Get-ReleaseVersionFromGradleText -Text $text -Source $versionPath
        $updated = Set-ReleaseVersionInGradleText `
            -Text $text `
            -CurrentVersionName $current.versionName `
            -CurrentVersionCode $current.versionCode `
            -TargetVersionName ([string] $stateResult.targetVersion) `
            -TargetVersionCode ([int] $stateResult.targetVersionCode)
        Write-ReleaseTextAtomic -Path $versionPath -Text $updated
        $stateResult = & $StateResolver -ProjectDir $ProjectDir
        if ($stateResult.state -ne 'PreparedWorktree') {
            throw "版本写入后预期 PreparedWorktree，实际为 $($stateResult.state)"
        }
    }

    if ($null -eq $GateStepRunner) {
        throw '发布准备缺少门禁步骤执行器'
    }
    $steps = [System.Collections.Generic.List[object]]::new()
    $artifact = [pscustomobject]@{}
    $context = [pscustomobject]@{
        projectDir = [System.IO.Path]::GetFullPath($ProjectDir)
        targetVersion = [string] $stateResult.targetVersion
        targetVersionCode = [int] $stateResult.targetVersionCode
        commandRunner = $CommandRunner
        artifact = $null
    }
    foreach ($definition in @(Get-ReleaseGateStepDefinitions)) {
        $step = & $GateStepRunner -Definition $definition -Context $context
        if ($null -eq $step -or [string] (Get-ReleaseProperty $step 'id') -cne [string] $definition.id) {
            throw "门禁步骤返回无效：$($definition.id)"
        }
        $steps.Add($step)
        if ([int] (Get-ReleaseProperty $step 'exitCode' -1) -ne 0) {
            throw "门禁步骤失败：$($definition.id)"
        }
        if ($definition.id -eq 'verifyApk') {
            $artifact = Get-ReleaseProperty $step 'artifact' ([pscustomobject]@{})
            $context.artifact = $artifact
        }
    }

    if ($Stage -eq 'PrepareRecovery') {
        $after = & $RecoverySnapshotResolver -ProjectDir $ProjectDir -StateResult $stateResult
        Assert-ReleaseRecoverySnapshot `
            -Snapshot $after `
            -CandidateSha $CandidateSha `
            -OldTagObjectId $ExpectedOldTagObjectId `
            -OldTagPeeledSha $ExpectedOldTagPeeledSha `
            -PrePushOriginMainSha $prePushOriginMainSha
        $pending = New-ReleasePendingRecoveryEvidence `
            -StateResult $stateResult `
            -Before $before `
            -After $after `
            -Steps @($steps) `
            -Artifact $artifact `
            -CandidateSha $CandidateSha `
            -OldTagObjectId $ExpectedOldTagObjectId `
            -OldTagPeeledSha $ExpectedOldTagPeeledSha `
            -PrePushOriginMainSha $prePushOriginMainSha `
            -StartedAt ([string] $steps[0].startedAt)
        Write-ReleaseJsonAtomic -Path "$RecoveryEvidencePath.pending" -Value $pending
        return New-ReleasePrepareResult `
            -StateResult $stateResult `
            -Stage $Stage `
            -Ok $true `
            -ReasonCode 'RECOVERY_WALKTHROUGH_REQUIRED' `
            -Message '自动恢复门禁已通过，必须安装同一 APK 并完成人工走查' `
            -Artifact $artifact `
            -Gate (Get-ReleaseProperty $pending 'gate')
    }

    return New-ReleasePrepareResult `
        -StateResult $stateResult `
        -Stage $Stage `
        -Ok $true `
        -ReasonCode 'RELEASE_GATE_PASSED' `
        -Message '固定签名发布门禁已完成' `
        -Artifact $artifact `
        -Gate ([pscustomobject]@{ steps = @($steps) })
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        throw '必须提供 -OutputPath；stdout 不作为机器接口'
    }
    $projectDir = Split-Path -Parent $PSScriptRoot
    $result = $null
    $exitCode = 0
    try {
        $result = Invoke-ReleasePreparation `
            -Stage $Stage `
            -ProjectDir $projectDir `
            -CandidateSha $CandidateSha `
            -ExpectedOldTagObjectId $ExpectedOldTagObjectId `
            -ExpectedOldTagPeeledSha $ExpectedOldTagPeeledSha `
            -RecoveryEvidencePath $RecoveryEvidencePath `
            -FinalizeWalkthrough:$FinalizeWalkthrough `
            -ConfirmWalkthrough $ConfirmWalkthrough
        if (-not $result.ok) { $exitCode = 1 }
    } catch {
        $exitCode = 1
        $fallback = [pscustomobject]@{
            state = 'Conflict'
            reasonCode = 'PREPARE_FAILED'
            targetVersion = ''
            targetVersionCode = 0
            targetTag = ''
            targetSha = $null
            baselineTag = ''
            cacheStatus = 'Missing'
            tag = [pscustomobject]@{}
            run = [pscustomobject]@{}
            release = [pscustomobject]@{}
            recovery = [pscustomobject]@{}
            transition = 'Stop'
        }
        $result = New-ReleasePrepareResult `
            -StateResult $fallback `
            -Stage $Stage `
            -Ok $false `
            -ReasonCode 'PREPARE_FAILED' `
            -Message $_.Exception.Message
    }
    Write-ReleaseOutputs -ProjectDir $projectDir -OutputPath $OutputPath -Output $result
    Write-Host "$($result.stage): state=$($result.state) reason=$($result.reasonCode) ok=$($result.ok)"
    exit $exitCode
}
