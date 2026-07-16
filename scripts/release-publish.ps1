param(
    [ValidateSet(
        'Status',
        'PushMain',
        'PushTag',
        'WaitTagActions',
        'PushRecoveryMain',
        'RecoverTag',
        'PublishRelease',
        'VerifyRelease',
        'Cleanup'
    )]
    [string] $Stage = 'Status',
    [string] $OutputPath,
    [int] $DiscoveryTimeoutMinutes = 5,
    [int] $CompletionTimeoutMinutes = 60,
    [int] $PollSeconds = 15,
    [string] $RecoveryEvidencePath,
    [string] $ExpectedOldTagObjectId,
    [string] $ExpectedOldTagPeeledSha,
    [string] $NewTargetSha,
    [string] $ExpectedRecoveryEvidenceSha256,
    [string] $ConfirmTagRecovery
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'release-state.ps1')
. (Join-Path $PSScriptRoot 'release-verify-apk.ps1')

function Invoke-ReleaseCommand {
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

function Get-ReleaseVersionFromGradleText {
    param(
        [Parameter(Mandatory)] [string] $Text,
        [Parameter(Mandatory)] [string] $Source
    )

    $nameMatches = [regex]::Matches($Text, '(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$')
    $codeMatches = [regex]::Matches($Text, '(?m)^\s*versionCode\s*=\s*([0-9]+)\s*$')
    if ($nameMatches.Count -ne 1 -or $codeMatches.Count -ne 1) {
        throw "$Source 中 versionName/versionCode 必须各恰有一个"
    }

    $versionName = $nameMatches[0].Groups[1].Value
    ConvertTo-ReleaseVersion -Value $versionName | Out-Null
    $versionCode = 0
    if (-not [int]::TryParse($codeMatches[0].Groups[1].Value, [ref] $versionCode) -or $versionCode -le 0) {
        throw "$Source 中 versionCode 无效"
    }
    return [pscustomobject]@{
        versionName = $versionName
        versionCode = $versionCode
    }
}

function Write-ReleaseJsonAtomic {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [object] $Value
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $directory = [System.IO.Path]::GetDirectoryName($fullPath)
    if ([string]::IsNullOrWhiteSpace($directory)) {
        throw "JSON 输出路径缺少父目录：$Path"
    }
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $temporaryPath = Join-Path $directory ".$([System.IO.Path]::GetFileName($fullPath)).$([guid]::NewGuid()).tmp"
    try {
        $json = $Value | ConvertTo-Json -Depth 100
        [System.IO.File]::WriteAllText(
            $temporaryPath,
            "$json`n",
            [System.Text.UTF8Encoding]::new($false)
        )
        [System.IO.File]::Move($temporaryPath, $fullPath, $true)
    } finally {
        Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
    }
}

function New-ReleaseMachineOutput {
    param(
        [Parameter(Mandatory)] [string] $Stage,
        [Parameter(Mandatory)] [object] $StateResult,
        [Parameter(Mandatory)] [bool] $Ok,
        [Parameter(Mandatory)] [string] $Message
    )

    return [pscustomobject][ordered]@{
        schemaVersion = 1
        ok = $Ok
        stage = $Stage
        state = [string] $StateResult.state
        reasonCode = [string] $StateResult.reasonCode
        message = $Message
        targetVersion = [string] $StateResult.targetVersion
        targetVersionCode = [int] $StateResult.targetVersionCode
        targetTag = [string] $StateResult.targetTag
        targetSha = Get-ReleaseProperty $StateResult 'targetSha'
        baselineTag = [string] $StateResult.baselineTag
        cacheStatus = [string] $StateResult.cacheStatus
        tag = Get-ReleaseProperty $StateResult 'tag' ([pscustomobject]@{})
        run = Get-ReleaseProperty $StateResult 'run' ([pscustomobject]@{})
        artifact = Get-ReleaseProperty $StateResult 'artifact' ([pscustomobject]@{})
        release = Get-ReleaseProperty $StateResult 'release' ([pscustomobject]@{})
        recovery = Get-ReleaseProperty $StateResult 'recovery' ([pscustomobject]@{})
        transition = [string] $StateResult.transition
        observedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
}

function Invoke-ReleaseCheckedCommand {
    param(
        [Parameter(Mandatory)] [string] $FilePath,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Description,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $result = & $CommandRunner -FilePath $FilePath -Arguments $Arguments
    Assert-ReleaseCommandSucceeded -Result $result -Description $Description
    return [string] $result.output
}

function Get-ReleaseVersionAtGitRef {
    param(
        [Parameter(Mandatory)] [string] $Ref,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $text = Invoke-ReleaseCheckedCommand `
        -FilePath 'git' `
        -Arguments @('show', "$Ref`:app/build.gradle.kts") `
        -Description "读取 $Ref 中的版本" `
        -CommandRunner $CommandRunner
    return Get-ReleaseVersionFromGradleText -Text $text -Source "$Ref`:app/build.gradle.kts"
}

function Get-ReleaseDirtyPaths {
    param([scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand})

    $output = Invoke-ReleaseCheckedCommand `
        -FilePath 'git' `
        -Arguments @('-c', 'core.quotepath=false', 'status', '--porcelain=v1', '--untracked-files=all') `
        -Description '读取工作树状态' `
        -CommandRunner $CommandRunner
    if ([string]::IsNullOrWhiteSpace($output)) {
        return @()
    }
    return @(
        $output -split "`r?`n" |
            Where-Object { $_.Length -ge 4 } |
            ForEach-Object {
                $path = $_.Substring(3)
                if ($path.Contains(' -> ')) {
                    $path = $path.Split(' -> ', 2)[1]
                }
                $path.Trim('"')
            } |
            Sort-Object -Unique
    )
}

function Test-ReleaseGitAncestor {
    param(
        [Parameter(Mandatory)] [string] $Ancestor,
        [Parameter(Mandatory)] [string] $Descendant,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $result = & $CommandRunner -FilePath 'git' -Arguments @('merge-base', '--is-ancestor', $Ancestor, $Descendant)
    if ([int] $result.exitCode -eq 0) { return $true }
    if ([int] $result.exitCode -eq 1) { return $false }
    throw "git merge-base 失败：$($result.output)"
}

function Get-ReleaseRemoteTags {
    param([scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand})

    $output = Invoke-ReleaseCheckedCommand `
        -FilePath 'git' `
        -Arguments @('ls-remote', '--tags', 'origin', 'refs/tags/v*') `
        -Description '查询远端稳定 Tag' `
        -CommandRunner $CommandRunner
    $tags = @{}
    foreach ($line in @($output -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
        $parts = $line -split "\s+", 2
        if ($parts.Count -ne 2 -or -not (Test-FullGitSha $parts[0])) { continue }
        $ref = $parts[1]
        $peeled = $ref.EndsWith('^{}')
        if ($peeled) { $ref = $ref.Substring(0, $ref.Length - 3) }
        if ($ref -notmatch '^refs/tags/(v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))$') {
            continue
        }
        $tagName = $Matches[1]
        if (-not $tags.ContainsKey($tagName)) {
            $tags[$tagName] = [ordered]@{ tagName = $tagName; objectId = $null; peeledSha = $null }
        }
        if ($peeled) {
            $tags[$tagName].peeledSha = $parts[0]
        } else {
            $tags[$tagName].objectId = $parts[0]
        }
    }
    return $tags
}

function Invoke-ReleaseGhJson {
    param(
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Description,
        [switch] $AllowNotFound,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $result = & $CommandRunner -FilePath 'gh' -Arguments $Arguments
    if ([int] $result.exitCode -ne 0) {
        if ($AllowNotFound -and [string] $result.output -match '(?i)404|not found') {
            return $null
        }
        throw "$Description 失败：$($result.output)"
    }
    if ([string]::IsNullOrWhiteSpace([string] $result.output)) {
        return $null
    }
    return [string] $result.output | ConvertFrom-Json -Depth 100
}

function Get-ReleaseRepositorySlug {
    param([scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand})

    $slug = Invoke-ReleaseCheckedCommand `
        -FilePath 'gh' `
        -Arguments @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner') `
        -Description '解析 GitHub 仓库' `
        -CommandRunner $CommandRunner
    $slug = $slug.Trim()
    if ($slug -cnotmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
        throw "GitHub 仓库标识无效：$slug"
    }
    return $slug
}

function Get-ReleaseTagRunFacts {
    param(
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [string] $TargetTag,
        [Parameter(Mandatory)] [string] $TargetSha,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $data = Invoke-ReleaseGhJson `
        -Arguments @(
            'api', '--method', 'GET',
            "repos/$Repository/actions/workflows/android-benchmark.yml/runs",
            '-f', 'event=push', '-f', "branch=$TargetTag", '-f', 'per_page=100'
        ) `
        -Description '查询稳定 Tag Actions' `
        -CommandRunner $CommandRunner
    $matches = @(
        @(Get-ReleaseProperty $data 'workflow_runs' @()) |
            Where-Object {
                (Get-ReleaseProperty $_ 'event') -ceq 'push' -and
                (Get-ReleaseProperty $_ 'head_branch') -ceq $TargetTag -and
                (Get-ReleaseProperty $_ 'head_sha') -ceq $TargetSha -and
                ([string] (Get-ReleaseProperty $_ 'path')).EndsWith('/android-benchmark.yml')
            } |
            Sort-Object `
                @{ Expression = { [int64] (Get-ReleaseProperty $_ 'id' 0) }; Descending = $true },
                @{ Expression = { [int] (Get-ReleaseProperty $_ 'run_attempt' 0) }; Descending = $true }
    )
    if ($matches.Count -eq 0) {
        return [pscustomobject]@{ found = $false }
    }
    $run = $matches[0]
    return [pscustomobject]@{
        found = $true
        workflow = 'android-benchmark.yml'
        event = [string] (Get-ReleaseProperty $run 'event')
        headBranch = [string] (Get-ReleaseProperty $run 'head_branch')
        headSha = [string] (Get-ReleaseProperty $run 'head_sha')
        status = [string] (Get-ReleaseProperty $run 'status')
        conclusion = Get-ReleaseProperty $run 'conclusion'
        databaseId = [int64] (Get-ReleaseProperty $run 'id')
        runNumber = [int64] (Get-ReleaseProperty $run 'run_number')
        attempt = [int] (Get-ReleaseProperty $run 'run_attempt' 1)
        createdAt = Get-ReleaseProperty $run 'created_at'
        completedAt = Get-ReleaseProperty $run 'updated_at'
        htmlUrl = Get-ReleaseProperty $run 'html_url'
    }
}

function Get-ReleaseArtifactFacts {
    param(
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [object] $Run,
        [Parameter(Mandatory)] [string] $TargetVersion,
        [Parameter(Mandatory)] [int] $TargetVersionCode,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    if ((Get-ReleaseProperty $Run 'found' $false) -ne $true) {
        return [pscustomobject]@{ count = 0; verified = $false }
    }
    $runId = [int64] (Get-ReleaseProperty $Run 'databaseId')
    $data = Invoke-ReleaseGhJson `
        -Arguments @('api', "repos/$Repository/actions/runs/$runId/artifacts") `
        -Description '查询 Tag Actions Artifact' `
        -CommandRunner $CommandRunner
    $artifacts = @(@(Get-ReleaseProperty $data 'artifacts' @()))
    $expectedPrefix = "benchmark-apk-$TargetVersion-$TargetVersionCode-run"
    $matching = @($artifacts | Where-Object { ([string] (Get-ReleaseProperty $_ 'name')).StartsWith($expectedPrefix) })
    $single = if ($matching.Count -eq 1) { $matching[0] } else { $null }
    return [pscustomobject]@{
        count = $matching.Count
        id = Get-ReleaseProperty $single 'id'
        name = Get-ReleaseProperty $single 'name'
        expired = if ($null -eq $single) { $false } else { [bool] (Get-ReleaseProperty $single 'expired' $false) }
        nameMatches = $matching.Count -eq 1
        versionMatches = $matching.Count -eq 1
        verified = $false
        sha256 = $null
        localPath = $null
    }
}

function Get-ReleaseFactsFromGitHubRelease {
    param(
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [string] $TargetTag,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $release = Invoke-ReleaseGhJson `
        -Arguments @('api', "repos/$Repository/releases/tags/$TargetTag") `
        -Description '查询目标 GitHub Release' `
        -AllowNotFound `
        -CommandRunner $CommandRunner
    if ($null -eq $release) {
        return [pscustomobject]@{ exists = $false }
    }
    $assets = @(@(Get-ReleaseProperty $release 'assets' @()))
    $apkAssets = @($assets | Where-Object { ([string] (Get-ReleaseProperty $_ 'name')) -cmatch '\.apk$' })
    $latest = Invoke-ReleaseGhJson `
        -Arguments @('api', "repos/$Repository/releases/latest") `
        -Description '查询 GitHub latest Release' `
        -CommandRunner $CommandRunner
    return [pscustomobject]@{
        exists = $true
        id = Get-ReleaseProperty $release 'id'
        tagName = [string] (Get-ReleaseProperty $release 'tag_name')
        draft = [bool] (Get-ReleaseProperty $release 'draft' $false)
        prerelease = [bool] (Get-ReleaseProperty $release 'prerelease' $false)
        htmlUrl = Get-ReleaseProperty $release 'html_url'
        apkCount = $apkAssets.Count
        apkName = if ($apkAssets.Count -eq 1) { Get-ReleaseProperty $apkAssets[0] 'name' } else { $null }
        apkUrl = if ($apkAssets.Count -eq 1) { Get-ReleaseProperty $apkAssets[0] 'url' } else { $null }
        isLatest = (Get-ReleaseProperty $latest 'tag_name') -ceq $TargetTag
        deepVerified = $false
        apkSha256 = $null
    }
}

function Get-ReleaseAndroidHome {
    param([string] $ProjectDir)

    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { return $candidate }
    }
    $localProperties = Join-Path $ProjectDir 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $match = [regex]::Match(
            (Get-Content -LiteralPath $localProperties -Raw -Encoding utf8),
            '(?m)^sdk\.dir=(.+)$'
        )
        if ($match.Success) { return $match.Groups[1].Value.Trim().Replace('\\', '\') }
    }
    return $null
}

function Confirm-ReleaseArtifact {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [object] $Run,
        [Parameter(Mandatory)] [object] $Artifact,
        [Parameter(Mandatory)] [string] $TargetTag,
        [Parameter(Mandatory)] [string] $TargetVersion,
        [Parameter(Mandatory)] [int] $TargetVersionCode,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    if ([int] (Get-ReleaseProperty $Artifact 'count' 0) -ne 1 -or
        [bool] (Get-ReleaseProperty $Artifact 'expired' $false)) {
        return $Artifact
    }
    $runId = [int64] (Get-ReleaseProperty $Run 'databaseId')
    $attempt = [int] (Get-ReleaseProperty $Run 'attempt' 1)
    $artifactName = [string] (Get-ReleaseProperty $Artifact 'name')
    $directory = Join-Path $ProjectDir "app/build/reports/release-artifacts/$TargetTag-run$runId-attempt$attempt"
    Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $download = & $CommandRunner -FilePath 'gh' -Arguments @(
        'run', 'download', [string] $runId, '--repo', $Repository,
        '--name', $artifactName, '--dir', $directory
    )
    Assert-ReleaseCommandSucceeded $download '下载 Tag Actions Artifact'
    $apks = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter '*.apk')
    if ($apks.Count -ne 1 -or $apks[0].Name -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.apk$') {
        throw "Tag Actions Artifact 必须恰有一个时间戳 APK，实际为 $($apks.Count)"
    }
    $verified = Invoke-ReleaseApkVerification `
        -ApkPath $apks[0].FullName `
        -ExpectedVersionName $TargetVersion `
        -ExpectedVersionCode $TargetVersionCode `
        -AndroidHome (Get-ReleaseAndroidHome -ProjectDir $ProjectDir)
    return [pscustomobject]@{
        count = 1
        id = Get-ReleaseProperty $Artifact 'id'
        name = $artifactName
        expired = $false
        nameMatches = $true
        versionMatches = $true
        verified = $true
        sha256 = $verified.sha256
        localPath = $verified.localPath
        fileName = $verified.fileName
        packageName = $verified.packageName
        versionName = $verified.versionName
        versionCode = $verified.versionCode
        signerSha256 = $verified.signerSha256
        runId = $runId
        attempt = $attempt
    }
}

function Get-ReleaseStableReleases {
    param(
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [hashtable] $RemoteTags,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [System.Collections.Generic.List[string]] $Conflicts,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $data = Invoke-ReleaseGhJson `
        -Arguments @('api', "repos/$Repository/releases?per_page=100") `
        -Description '查询 GitHub Releases' `
        -CommandRunner $CommandRunner
    $stable = [System.Collections.Generic.List[object]]::new()
    foreach ($release in @($data)) {
        if ([bool] (Get-ReleaseProperty $release 'draft' $false) -or
            [bool] (Get-ReleaseProperty $release 'prerelease' $false)) {
            continue
        }
        $tagName = [string] (Get-ReleaseProperty $release 'tag_name')
        if ($tagName -cnotmatch '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
            continue
        }
        if (-not $RemoteTags.ContainsKey($tagName)) {
            $Conflicts.Add("RELEASE_TAG_MISSING:$tagName")
            continue
        }
        $remote = $RemoteTags[$tagName]
        $objectId = [string] $remote.objectId
        $peeledSha = [string] $remote.peeledSha
        if (-not (Test-FullGitSha $objectId) -or -not (Test-FullGitSha $peeledSha)) {
            $Conflicts.Add("RELEASE_TAG_NOT_ANNOTATED:$tagName")
            continue
        }
        Invoke-ReleaseCheckedCommand `
            -FilePath 'git' `
            -Arguments @('fetch', '--no-tags', 'origin', $peeledSha) `
            -Description "获取 $tagName 的提交对象" `
            -CommandRunner $CommandRunner | Out-Null
        $version = Get-ReleaseVersionAtGitRef -Ref $peeledSha -CommandRunner $CommandRunner
        if ($tagName -cne "v$($version.versionName)") {
            $Conflicts.Add("RELEASE_VERSION_MISMATCH:$tagName")
            continue
        }
        $parsed = ConvertTo-ReleaseVersion -Value $version.versionName
        $stable.Add([pscustomobject]@{
            tagName = $tagName
            versionName = $version.versionName
            versionCode = $version.versionCode
            major = $parsed.major
            minor = $parsed.minor
            patch = $parsed.patch
            objectId = $objectId
            peeledSha = $peeledSha
            release = $release
        })
    }
    return @(
        $stable |
            Sort-Object `
                @{ Expression = 'major'; Ascending = $true },
                @{ Expression = 'minor'; Ascending = $true },
                @{ Expression = 'patch'; Ascending = $true }
    )
}

function Get-ReleaseEvidence {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [string] $TargetTag,
        [Parameter(Mandatory)] [string] $CandidateSha,
        [Parameter(Mandatory)] [string] $OldTagObjectId,
        [Parameter(Mandatory)] [string] $OldTagPeeledSha,
        [Parameter(Mandatory)] [string] $CurrentOriginMainSha,
        [Parameter(Mandatory)] [int] $TargetVersionCode,
        [Parameter(Mandatory)] [string] $TargetVersion,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $relative = "release-evidence/$TargetTag-$CandidateSha.json"
    $path = Invoke-ReleaseCheckedCommand `
        -FilePath 'git' `
        -Arguments @('rev-parse', '--path-format=absolute', '--git-path', $relative) `
        -Description '解析恢复门禁证据路径' `
        -CommandRunner $CommandRunner
    $path = $path.Trim()
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return [pscustomobject]@{
            oldTagObjectId = $OldTagObjectId
            oldTagPeeledSha = $OldTagPeeledSha
            candidateSha = $CandidateSha
            prePushOriginMainSha = $null
            gateEvidencePath = $path
            gateEvidenceSha256 = $null
            gateEvidenceStatus = 'Missing'
            authorizationRequired = $false
            invalidatedSha = $null
        }
    }
    try {
        $evidence = Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json -Depth 100
        $prePushOriginMainSha = [string] (Get-ReleaseProperty $evidence 'prePushOriginMainSha')
        $bindingValid = $false
        if ($CurrentOriginMainSha -cne $CandidateSha) {
            $bindingValid = $prePushOriginMainSha -ceq $CurrentOriginMainSha
        } elseif (Test-FullGitSha $prePushOriginMainSha) {
            $bindingValid =
                $prePushOriginMainSha -cne $CandidateSha -and
                (Test-ReleaseGitAncestor -Ancestor $OldTagPeeledSha -Descendant $prePushOriginMainSha -CommandRunner $CommandRunner) -and
                (Test-ReleaseGitAncestor -Ancestor $prePushOriginMainSha -Descendant $CandidateSha -CommandRunner $CommandRunner)
        }
        if (-not $bindingValid) {
            return [pscustomobject]@{
                oldTagObjectId = $OldTagObjectId
                oldTagPeeledSha = $OldTagPeeledSha
                candidateSha = $CandidateSha
                prePushOriginMainSha = $prePushOriginMainSha
                gateEvidencePath = $path
                gateEvidenceSha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
                gateEvidenceStatus = 'Stale'
                gateEvidenceReasonCode = 'RECOVERY_BINDING_PREPUSHORIGINMAINSHA'
                authorizationRequired = $false
                invalidatedSha = $null
            }
        }
        $expected = [pscustomobject]@{
            targetTag = $TargetTag
            targetVersion = $TargetVersion
            targetVersionCode = $TargetVersionCode
            candidateSha = $CandidateSha
            oldTagObjectId = $OldTagObjectId
            oldTagPeeledSha = $OldTagPeeledSha
            prePushOriginMainSha = $prePushOriginMainSha
            signerSha256 = $script:ReleaseSignerSha256
        }
        $validation = Test-RecoveryEvidence -Evidence $evidence -Expected $expected
        $fileSha = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        return [pscustomobject]@{
            oldTagObjectId = $OldTagObjectId
            oldTagPeeledSha = $OldTagPeeledSha
            candidateSha = $CandidateSha
            prePushOriginMainSha = Get-ReleaseProperty $evidence 'prePushOriginMainSha'
            gateEvidencePath = $path
            gateEvidenceSha256 = $fileSha
            gateEvidenceStatus = [string] $validation.status
            gateEvidenceReasonCode = [string] $validation.reasonCode
            authorizationRequired = $false
            invalidatedSha = $null
        }
    } catch {
        return [pscustomobject]@{
            oldTagObjectId = $OldTagObjectId
            oldTagPeeledSha = $OldTagPeeledSha
            candidateSha = $CandidateSha
            prePushOriginMainSha = $null
            gateEvidencePath = $path
            gateEvidenceSha256 = $null
            gateEvidenceStatus = 'Stale'
            gateEvidenceReasonCode = 'RECOVERY_EVIDENCE_PARSE_FAILED'
            authorizationRequired = $false
            invalidatedSha = $null
        }
    }
}

function Get-ReleaseToolchainFacts {
    param([Parameter(Mandatory)] [string] $ProjectDir)

    $catalogPath = Join-Path $ProjectDir 'gradle/libs.versions.toml'
    $catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding utf8
    $androidHome = Get-ReleaseAndroidHome -ProjectDir $ProjectDir
    $errors = [System.Collections.Generic.List[string]]::new()
    $configuredMatch = [regex]::Match($catalog, '(?m)^buildTools\s*=\s*"([^"]+)"\s*$')
    $configuredVersion = if ($configuredMatch.Success) { $configuredMatch.Groups[1].Value } else { $null }
    if ($null -eq $configuredVersion) { $errors.Add('CATALOG_BUILD_TOOLS_MISSING') }
    try {
        Get-ReleaseBuildToolPath -AndroidHome $androidHome -Name 'aapt2' | Out-Null
        Get-ReleaseBuildToolPath -AndroidHome $androidHome -Name 'apksigner' | Out-Null
    } catch {
        $errors.Add('FIXED_BUILD_TOOLS_MISSING')
    }
    return [pscustomobject]@{
        valid = $errors.Count -eq 0
        buildToolsVersion = $script:ReleaseBuildToolsVersion
        configuredBuildToolsVersion = $configuredVersion
        errors = @($errors)
    }
}

function Get-ReleaseCacheStatus {
    param(
        [Parameter(Mandatory)] [string] $CachePath,
        [Parameter(Mandatory)] [object] $StateResult
    )

    if (-not (Test-Path -LiteralPath $CachePath -PathType Leaf)) { return 'Missing' }
    try {
        $cached = Get-Content -LiteralPath $CachePath -Raw -Encoding utf8 | ConvertFrom-Json -Depth 100
        foreach ($name in @('targetVersion', 'targetVersionCode', 'targetTag', 'targetSha', 'baselineTag')) {
            if ((Get-ReleaseProperty $cached $name) -cne (Get-ReleaseProperty $StateResult $name)) {
                return 'Stale'
            }
        }
        return 'Consistent'
    } catch {
        return 'Stale'
    }
}

function Get-ReleaseFacts {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [switch] $DeepVerifyRelease,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    Push-Location $ProjectDir
    try {
        Invoke-ReleaseCheckedCommand `
            -FilePath 'git' `
            -Arguments @('fetch', '--prune', 'origin', 'refs/heads/main:refs/remotes/origin/main') `
            -Description '刷新 origin/main' `
            -CommandRunner $CommandRunner | Out-Null

        $conflicts = [System.Collections.Generic.List[string]]::new()
        $branch = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('branch', '--show-current') -Description '读取当前分支' -CommandRunner $CommandRunner).Trim()
        $headSha = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('rev-parse', 'HEAD') -Description '读取 HEAD' -CommandRunner $CommandRunner).Trim()
        $originMainSha = (Invoke-ReleaseCheckedCommand -FilePath 'git' -Arguments @('rev-parse', 'origin/main') -Description '读取 origin/main' -CommandRunner $CommandRunner).Trim()
        if ($branch -cne 'main') { $conflicts.Add("BRANCH_NOT_MAIN:$branch") }
        if (-not (Test-FullGitSha $headSha) -or -not (Test-FullGitSha $originMainSha)) {
            $conflicts.Add('INVALID_HEAD_OR_ORIGIN_SHA')
        }
        $headDescendsOrigin = Test-ReleaseGitAncestor -Ancestor $originMainSha -Descendant $headSha -CommandRunner $CommandRunner
        if (-not $headDescendsOrigin) { $conflicts.Add('HEAD_NOT_ORIGIN_MAIN_DESCENDANT') }

        $dirtyPaths = @(Get-ReleaseDirtyPaths -CommandRunner $CommandRunner)
        $worktreeVersion = Get-ReleaseVersionFromGradleText `
            -Text (Get-Content -LiteralPath (Join-Path $ProjectDir 'app/build.gradle.kts') -Raw -Encoding utf8) `
            -Source 'app/build.gradle.kts'
        $headVersion = Get-ReleaseVersionAtGitRef -Ref $headSha -CommandRunner $CommandRunner
        $originVersion = Get-ReleaseVersionAtGitRef -Ref $originMainSha -CommandRunner $CommandRunner
        $remoteTags = Get-ReleaseRemoteTags -CommandRunner $CommandRunner
        $repositorySlug = Get-ReleaseRepositorySlug -CommandRunner $CommandRunner
        $stableReleases = @(Get-ReleaseStableReleases `
            -Repository $repositorySlug `
            -RemoteTags $remoteTags `
            -Conflicts $conflicts `
            -CommandRunner $CommandRunner)
        if ($stableReleases.Count -eq 0) {
            throw '未找到可作为基线的完整稳定 Release'
        }

        $latestRelease = $stableReleases[-1]
        $targetVersion = $null
        $baselineRelease = $latestRelease
        $completedLatest =
            $headSha -ceq $latestRelease.peeledSha -and
            $originMainSha -ceq $latestRelease.peeledSha -and
            $worktreeVersion.versionName -ceq $latestRelease.versionName -and
            $headVersion.versionName -ceq $latestRelease.versionName -and
            $originVersion.versionName -ceq $latestRelease.versionName
        if ($completedLatest) {
            if ($stableReleases.Count -lt 2) { throw '已完成目标 Release 但缺少前一稳定基线' }
            $targetVersion = $latestRelease.versionName
            $baselineRelease = $stableReleases[-2]
        } else {
            $candidateVersions = [System.Collections.Generic.List[string]]::new()
            foreach ($version in @($worktreeVersion.versionName, $headVersion.versionName, $originVersion.versionName)) {
                if ((Compare-ReleaseVersion $version $latestRelease.versionName) -gt 0) {
                    $candidateVersions.Add($version)
                }
            }
            foreach ($tagName in @($remoteTags.Keys)) {
                $version = $tagName.Substring(1)
                if ((Compare-ReleaseVersion $version $latestRelease.versionName) -gt 0) {
                    $candidateVersions.Add($version)
                }
            }
            $uniqueCandidates = @($candidateVersions | Sort-Object -Unique)
            if ($uniqueCandidates.Count -gt 1) {
                $conflicts.Add("MULTIPLE_TARGET_VERSIONS:$($uniqueCandidates -join ',')")
            }
            if ($uniqueCandidates.Count -gt 0) { $targetVersion = $uniqueCandidates[-1] }
        }

        $targetTag = if ($null -eq $targetVersion) { $null } else { "v$targetVersion" }
        $remoteTag = [pscustomobject]@{ exists = $false }
        $targetVersionCode = $null
        if ($null -ne $targetTag -and $remoteTags.ContainsKey($targetTag)) {
            $remote = $remoteTags[$targetTag]
            if (-not (Test-FullGitSha $remote.objectId) -or -not (Test-FullGitSha $remote.peeledSha)) {
                $conflicts.Add("TARGET_TAG_NOT_ANNOTATED:$targetTag")
            } else {
                Invoke-ReleaseCheckedCommand `
                    -FilePath 'git' `
                    -Arguments @('fetch', '--no-tags', 'origin', [string] $remote.peeledSha) `
                    -Description "获取 $targetTag 的提交对象" `
                    -CommandRunner $CommandRunner | Out-Null
                $tagVersion = Get-ReleaseVersionAtGitRef -Ref ([string] $remote.peeledSha) -CommandRunner $CommandRunner
                $targetVersionCode = $tagVersion.versionCode
                $remoteTag = [pscustomobject]@{
                    exists = $true
                    tagName = $targetTag
                    versionName = $tagVersion.versionName
                    versionCode = $tagVersion.versionCode
                    objectId = [string] $remote.objectId
                    peeledSha = [string] $remote.peeledSha
                }
            }
        }

        $releaseFacts = [pscustomobject]@{ exists = $false }
        if ($null -ne $targetTag) {
            $releaseFacts = Get-ReleaseFactsFromGitHubRelease `
                -Repository $repositorySlug `
                -TargetTag $targetTag `
                -CommandRunner $CommandRunner
        }
        $runFacts = [pscustomobject]@{ found = $false }
        $artifactFacts = [pscustomobject]@{ count = 0; verified = $false }
        if ([bool] (Get-ReleaseProperty $remoteTag 'exists' $false)) {
            $runFacts = Get-ReleaseTagRunFacts `
                -Repository $repositorySlug `
                -TargetTag $targetTag `
                -TargetSha ([string] $remoteTag.peeledSha) `
                -CommandRunner $CommandRunner
            if ((Get-ReleaseProperty $runFacts 'found' $false) -eq $true) {
                $artifactFacts = Get-ReleaseArtifactFacts `
                    -Repository $repositorySlug `
                    -Run $runFacts `
                    -TargetVersion $targetVersion `
                    -TargetVersionCode ([int] $targetVersionCode) `
                    -CommandRunner $CommandRunner
                if ((Get-ReleaseProperty $runFacts 'conclusion') -ceq 'success') {
                    $artifactFacts = Confirm-ReleaseArtifact `
                        -ProjectDir $ProjectDir `
                        -Repository $repositorySlug `
                        -Run $runFacts `
                        -Artifact $artifactFacts `
                        -TargetTag $targetTag `
                        -TargetVersion $targetVersion `
                        -TargetVersionCode ([int] $targetVersionCode) `
                        -CommandRunner $CommandRunner
                }
            }
        }

        $recovery = [pscustomobject]@{ gateEvidenceStatus = 'Missing' }
        if ([bool] (Get-ReleaseProperty $remoteTag 'exists' $false) -and
            (Get-ReleaseProperty $runFacts 'found' $false) -eq $true -and
            (Get-ReleaseProperty $runFacts 'conclusion') -notin @($null, 'success')) {
            $oldSha = [string] $remoteTag.peeledSha
            $sameVersion = $headVersion.versionName -ceq $targetVersion -and $headVersion.versionCode -eq $targetVersionCode
            $strictSuccessor = $headSha -cne $oldSha -and (Test-ReleaseGitAncestor -Ancestor $oldSha -Descendant $headSha -CommandRunner $CommandRunner)
            if ($sameVersion -and $strictSuccessor -and $headDescendsOrigin) {
                if ($dirtyPaths.Count -gt 0) { $conflicts.Add('RECOVERY_WORKTREE_NOT_CLEAN') }
                $recovery = Get-ReleaseEvidence `
                    -ProjectDir $ProjectDir `
                    -TargetTag $targetTag `
                    -CandidateSha $headSha `
                    -OldTagObjectId ([string] $remoteTag.objectId) `
                    -OldTagPeeledSha $oldSha `
                    -CurrentOriginMainSha $originMainSha `
                    -TargetVersionCode ([int] $targetVersionCode) `
                    -TargetVersion $targetVersion `
                    -CommandRunner $CommandRunner
                $recovery.authorizationRequired = $originMainSha -ceq $headSha
            } elseif ($headSha -cne $oldSha -or $originMainSha -cne $oldSha) {
                $conflicts.Add('INVALID_RECOVERY_CANDIDATE')
            }
        }

        $facts = [pscustomobject]@{
            baseline = [pscustomobject]@{
                tag = $baselineRelease.tagName
                versionName = $baselineRelease.versionName
                versionCode = $baselineRelease.versionCode
                sha = $baselineRelease.peeledSha
            }
            repository = [pscustomobject]@{
                branch = $branch
                repository = $repositorySlug
                headSha = $headSha
                originMainSha = $originMainSha
                headIsOriginMainDescendant = $headDescendsOrigin
                clean = $dirtyPaths.Count -eq 0
                dirtyPaths = $dirtyPaths
            }
            versions = [pscustomobject]@{
                worktree = $worktreeVersion
                head = $headVersion
                originMain = $originVersion
            }
            toolchain = Get-ReleaseToolchainFacts -ProjectDir $ProjectDir
            remoteTag = $remoteTag
            tagRun = $runFacts
            artifact = $artifactFacts
            release = $releaseFacts
            recovery = $recovery
            cacheStatus = 'Missing'
            conflicts = @($conflicts)
        }
        return $facts
    } finally {
        Pop-Location
    }
}

function Confirm-ReleaseAsset {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [string] $Repository,
        [Parameter(Mandatory)] [string] $TargetTag,
        [Parameter(Mandatory)] [string] $TargetVersion,
        [Parameter(Mandatory)] [int] $TargetVersionCode,
        [Parameter(Mandatory)] [object] $Artifact,
        [Parameter(Mandatory)] [object] $Release,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    if ((Get-ReleaseProperty $Release 'exists' $false) -ne $true -or
        [int] (Get-ReleaseProperty $Release 'apkCount' 0) -ne 1 -or
        (Get-ReleaseProperty $Artifact 'verified' $false) -ne $true) {
        return $Release
    }
    $directory = Join-Path $ProjectDir "app/build/reports/release-assets/$TargetTag"
    Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $download = & $CommandRunner -FilePath 'gh' -Arguments @(
        'release', 'download', $TargetTag, '--repo', $Repository,
        '--pattern', '*.apk', '--dir', $directory
    )
    Assert-ReleaseCommandSucceeded $download '下载正式 Release APK'
    $apks = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter '*.apk')
    if ($apks.Count -ne 1) {
        throw "正式 Release 必须恰有一个 APK，实际为 $($apks.Count)"
    }
    $verified = Invoke-ReleaseApkVerification `
        -ApkPath $apks[0].FullName `
        -ExpectedVersionName $TargetVersion `
        -ExpectedVersionCode $TargetVersionCode `
        -AndroidHome (Get-ReleaseAndroidHome -ProjectDir $ProjectDir)
    if ($verified.sha256 -cne (Get-ReleaseProperty $Artifact 'sha256')) {
        throw "正式 Release APK 与 Tag run Artifact SHA-256 不一致：release=$($verified.sha256) artifact=$($Artifact.sha256)"
    }
    return [pscustomobject]@{
        exists = $true
        id = Get-ReleaseProperty $Release 'id'
        tagName = [string] (Get-ReleaseProperty $Release 'tagName')
        draft = [bool] (Get-ReleaseProperty $Release 'draft' $false)
        prerelease = [bool] (Get-ReleaseProperty $Release 'prerelease' $false)
        htmlUrl = Get-ReleaseProperty $Release 'htmlUrl'
        apkCount = 1
        apkName = $verified.fileName
        apkUrl = Get-ReleaseProperty $Release 'apkUrl'
        isLatest = [bool] (Get-ReleaseProperty $Release 'isLatest' $false)
        deepVerified = $true
        apkSha256 = $verified.sha256
        packageName = $verified.packageName
        versionName = $verified.versionName
        versionCode = $verified.versionCode
        signerSha256 = $verified.signerSha256
        verifiedAt = $verified.verifiedAt
    }
}

function Get-ResolvedReleaseState {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [switch] $DeepVerifyRelease,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $facts = Get-ReleaseFacts `
        -ProjectDir $ProjectDir `
        -DeepVerifyRelease:$DeepVerifyRelease `
        -CommandRunner $CommandRunner
    $state = Resolve-ReleaseState -Facts $facts
    if ($DeepVerifyRelease -and (Get-ReleaseProperty $facts.release 'exists' $false) -eq $true) {
        $facts.release = Confirm-ReleaseAsset `
            -ProjectDir $ProjectDir `
            -Repository ([string] $facts.repository.repository) `
            -TargetTag $state.targetTag `
            -TargetVersion $state.targetVersion `
            -TargetVersionCode $state.targetVersionCode `
            -Artifact $facts.artifact `
            -Release $facts.release `
            -CommandRunner $CommandRunner
        $state = Resolve-ReleaseState -Facts $facts
    }
    $cachePath = Join-Path $ProjectDir 'app/build/reports/release-context.json'
    $facts.cacheStatus = Get-ReleaseCacheStatus -CachePath $cachePath -StateResult $state
    return Resolve-ReleaseState -Facts $facts
}

function Write-ReleaseOutputs {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [string] $OutputPath,
        [Parameter(Mandatory)] [object] $Output
    )

    Write-ReleaseJsonAtomic -Path $OutputPath -Value $Output
    Write-ReleaseJsonAtomic `
        -Path (Join-Path $ProjectDir 'app/build/reports/release-context.json') `
        -Value $Output
}

function New-ReleaseNotes {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [object] $StateResult,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $path = Join-Path $ProjectDir 'app/build/reports/release-notes.md'
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $log = Invoke-ReleaseCheckedCommand `
        -FilePath 'git' `
        -Arguments @('log', '--pretty=format:- %s', "$($StateResult.baselineTag)..$($StateResult.targetSha)") `
        -Description '生成 Release 变更摘要' `
        -CommandRunner $CommandRunner
    $body = @(
        "# 1memos $($StateResult.targetVersion)",
        '',
        "- versionCode: $($StateResult.targetVersionCode)",
        "- commit: $($StateResult.targetSha)",
        "- signer SHA-256: $script:ReleaseSignerSha256",
        '',
        '## 变更',
        '',
        $log.Trim()
    ) -join "`n"
    [System.IO.File]::WriteAllText($path, "$body`n", [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Invoke-ReleasePublication {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [object] $StateResult,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $state = [string] $StateResult.state
    if ($state -notin @('TagActionsSucceeded', 'ReleaseIncomplete')) {
        if ($state -in @('ReleasePublished', 'Completed')) {
            return New-ReleaseActionResult $false '正式 Release 已存在'
        }
        throw "PublishRelease 只接受 TagActionsSucceeded 或 ReleaseIncomplete，实际为 $state"
    }
    $artifactPath = [string] (Get-ReleaseProperty $StateResult.artifact 'localPath')
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        throw "发布前缺少已核验 Tag run APK：$artifactPath"
    }
    $repository = (Invoke-ReleaseCheckedCommand `
        -FilePath 'gh' `
        -Arguments @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner') `
        -Description '解析 GitHub 仓库' `
        -CommandRunner $CommandRunner).Trim()
    $notesPath = New-ReleaseNotes -ProjectDir $ProjectDir -StateResult $StateResult -CommandRunner $CommandRunner
    $assetName = [System.IO.Path]::GetFileName($artifactPath)
    if ($state -eq 'TagActionsSucceeded') {
        $result = & $CommandRunner -FilePath 'gh' -Arguments @(
            'release', 'create', $StateResult.targetTag,
            "$artifactPath#$assetName", '--repo', $repository, '--verify-tag',
            '--title', "1memos $($StateResult.targetVersion)",
            '--notes-file', $notesPath, '--latest'
        )
        Assert-ReleaseCommandSucceeded $result '创建正式 GitHub Release'
    } else {
        $result = & $CommandRunner -FilePath 'gh' -Arguments @(
            'release', 'upload', $StateResult.targetTag,
            "$artifactPath#$assetName", '--repo', $repository
        )
        Assert-ReleaseCommandSucceeded $result '补传正式 Release APK'
    }
    return New-ReleaseActionResult $true '正式 Release 已创建或补全'
}

function Invoke-ReleaseWorkflowWait {
    param(
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [int] $DiscoveryTimeoutMinutes,
        [Parameter(Mandatory)] [int] $CompletionTimeoutMinutes,
        [Parameter(Mandatory)] [int] $PollSeconds,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    if ($DiscoveryTimeoutMinutes -le 0 -or $CompletionTimeoutMinutes -le 0 -or $PollSeconds -le 0) {
        throw 'Actions 等待时间参数必须为正整数'
    }
    $discoveryDeadline = [DateTimeOffset]::UtcNow.AddMinutes($DiscoveryTimeoutMinutes)
    $completionDeadline = [DateTimeOffset]::UtcNow.AddMinutes($CompletionTimeoutMinutes)
    while ($true) {
        $state = Get-ResolvedReleaseState -ProjectDir $ProjectDir -CommandRunner $CommandRunner
        if ($state.state -in @(
            'TagActionsSucceeded', 'TagActionsFailed', 'TagArtifactUnavailable',
            'ReleaseIncomplete', 'ReleasePublished', 'Completed', 'Conflict'
        )) {
            return $state
        }
        if ($state.state -ne 'AwaitingTagActions') {
            throw "WaitTagActions 收到意外状态：$($state.state)"
        }
        $runFound = [bool] (Get-ReleaseProperty $state.run 'found' $false)
        $now = [DateTimeOffset]::UtcNow
        if (-not $runFound -and $now -ge $discoveryDeadline) {
            throw '等待稳定 Tag Actions run 发现超时'
        }
        if ($runFound -and $now -ge $completionDeadline) {
            throw '等待稳定 Tag Actions 完成超时'
        }
        Start-Sleep -Seconds $PollSeconds
    }
}

function Assert-ReleaseCommandSucceeded {
    param(
        [Parameter(Mandatory)] [object] $Result,
        [Parameter(Mandatory)] [string] $Description
    )

    if ([int] $Result.exitCode -ne 0) {
        throw "$Description 失败：$($Result.output)"
    }
}

function New-ReleaseActionResult {
    param(
        [Parameter(Mandatory)] [bool] $ActionPerformed,
        [Parameter(Mandatory)] [string] $Message
    )

    return [pscustomobject]@{
        actionPerformed = $ActionPerformed
        message = $Message
    }
}

function Invoke-ReleaseStageAction {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('PushMain', 'PushTag', 'PushRecoveryMain', 'RecoverTag')]
        [string] $Stage,
        [Parameter(Mandatory)] [object] $StateResult,
        [string] $ExpectedOldTagObjectId,
        [string] $ExpectedOldTagPeeledSha,
        [string] $NewTargetSha,
        [string] $ExpectedRecoveryEvidenceSha256,
        [string] $ConfirmTagRecovery,
        [scriptblock] $CommandRunner = ${function:Invoke-ReleaseCommand}
    )

    $state = [string] $StateResult.state
    $targetTag = [string] $StateResult.targetTag
    $targetVersion = [string] $StateResult.targetVersion
    $targetSha = Get-ReleaseProperty $StateResult 'targetSha'

    switch ($Stage) {
        'PushMain' {
            if ($state -ne 'Prepared') {
                if ($state -in @(
                    'MainPushed', 'AwaitingTagActions', 'TagActionsFailed',
                    'TagArtifactUnavailable', 'TagActionsSucceeded', 'ReleaseIncomplete',
                    'ReleasePublished', 'Completed'
                )) {
                    return New-ReleaseActionResult $false 'main 已处于目标或后续状态'
                }
                throw "PushMain 只接受 Prepared，实际为 $state"
            }
            $result = & $CommandRunner -FilePath 'git' -Arguments @(
                'push', 'origin', 'refs/heads/main:refs/heads/main'
            )
            Assert-ReleaseCommandSucceeded $result '推送 main'
            return New-ReleaseActionResult $true 'main 已普通推送'
        }
        'PushTag' {
            if ($state -ne 'MainPushed') {
                if ($state -in @(
                    'AwaitingTagActions', 'TagActionsFailed', 'TagArtifactUnavailable',
                    'TagActionsSucceeded', 'ReleaseIncomplete', 'ReleasePublished', 'Completed'
                )) {
                    return New-ReleaseActionResult $false '稳定 Tag 已存在或已进入后续状态'
                }
                throw "PushTag 只接受 MainPushed，实际为 $state"
            }
            if (-not (Test-FullGitSha $targetSha)) {
                throw "PushTag 的 targetSha 无效：$targetSha"
            }
            $expectedTagSubject = "1memos $targetVersion"
            $localTagResult = & $CommandRunner -FilePath 'git' -Arguments @(
                'for-each-ref', '--format=%(objecttype)%09%(*objectname)%09%(contents:subject)',
                "refs/tags/$targetTag"
            )
            Assert-ReleaseCommandSucceeded $localTagResult '检查本地稳定 Tag'
            $localTagLines = @(
                [string] $localTagResult.output -split "`r?`n" |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            )
            if ($localTagLines.Count -eq 0) {
                $tagResult = & $CommandRunner -FilePath 'git' -Arguments @(
                    'tag', '-a', $targetTag, [string] $targetSha, '-m', $expectedTagSubject
                )
                Assert-ReleaseCommandSucceeded $tagResult '创建 annotated Tag'
            } elseif ($localTagLines.Count -eq 1) {
                $localTagParts = @($localTagLines[0] -split "`t", 3)
                if ($localTagParts.Count -ne 3 -or
                    $localTagParts[0] -cne 'tag' -or
                    $localTagParts[1] -cne [string] $targetSha -or
                    $localTagParts[2] -cne $expectedTagSubject) {
                    throw '本地稳定 Tag 与目标提交、类型或消息不一致，禁止覆盖'
                }
            } else {
                throw '本地稳定 Tag 查询返回多个结果，禁止推送'
            }
            $pushResult = & $CommandRunner -FilePath 'git' -Arguments @(
                'push', 'origin', "refs/tags/$targetTag`:refs/tags/$targetTag"
            )
            Assert-ReleaseCommandSucceeded $pushResult '推送稳定 Tag'
            return New-ReleaseActionResult $true '稳定 Tag 已创建并普通推送'
        }
        'PushRecoveryMain' {
            if ($state -ne 'RecoveryPrepared') {
                if ($state -eq 'TagRecoveryRequired') {
                    return New-ReleaseActionResult $false '恢复候选 main 已推送'
                }
                throw "PushRecoveryMain 只接受 RecoveryPrepared，实际为 $state"
            }
            $result = & $CommandRunner -FilePath 'git' -Arguments @(
                'push', 'origin', 'refs/heads/main:refs/heads/main'
            )
            Assert-ReleaseCommandSucceeded $result '普通推送恢复候选 main'
            return New-ReleaseActionResult $true '恢复候选 main 已普通推送'
        }
        'RecoverTag' {
            if ($state -ne 'TagRecoveryRequired') {
                if ($state -in @(
                    'AwaitingTagActions', 'TagArtifactUnavailable', 'TagActionsSucceeded',
                    'ReleaseIncomplete', 'ReleasePublished', 'Completed'
                )) {
                    return New-ReleaseActionResult $false '稳定 Tag 已恢复或进入后续状态'
                }
                throw "RecoverTag 只接受 TagRecoveryRequired，实际为 $state"
            }

            $recovery = Get-ReleaseProperty $StateResult 'recovery' ([pscustomobject]@{})
            $actualOldObject = [string] (Get-ReleaseProperty $recovery 'oldTagObjectId')
            $actualOldPeeled = [string] (Get-ReleaseProperty $recovery 'oldTagPeeledSha')
            $actualCandidate = [string] (Get-ReleaseProperty $recovery 'candidateSha')
            $actualEvidenceSha = [string] (Get-ReleaseProperty $recovery 'gateEvidenceSha256')
            foreach ($sha in @($actualOldObject, $actualOldPeeled, $actualCandidate)) {
                if (-not (Test-FullGitSha $sha)) {
                    throw "恢复事实含无效 Git SHA：$sha"
                }
            }
            if ($actualEvidenceSha -cnotmatch '^[0-9a-f]{64}$') {
                throw "恢复门禁摘要无效：$actualEvidenceSha"
            }
            if ($ExpectedOldTagObjectId -cne $actualOldObject -or
                $ExpectedOldTagPeeledSha -cne $actualOldPeeled -or
                $NewTargetSha -cne $actualCandidate -or
                $ExpectedRecoveryEvidenceSha256 -cne $actualEvidenceSha) {
                throw 'RecoverTag 参数与现场恢复事实不一致'
            }
            $expectedConfirmation =
                "RECOVER_TAG:$targetTag`:$actualOldObject`:$actualOldPeeled`:$actualCandidate`:$actualEvidenceSha"
            if ($ConfirmTagRecovery -cne $expectedConfirmation) {
                throw 'RecoverTag 缺少与现场事实逐字匹配的书面确认串'
            }

            $tagResult = & $CommandRunner -FilePath 'git' -Arguments @(
                'tag', '-f', '-a', $targetTag, $actualCandidate, '-m', "1memos $targetVersion"
            )
            Assert-ReleaseCommandSucceeded $tagResult '更新本地 annotated Tag'
            $pushResult = & $CommandRunner -FilePath 'git' -Arguments @(
                'push', "--force-with-lease=refs/tags/$targetTag`:$actualOldObject",
                'origin', "refs/tags/$targetTag`:refs/tags/$targetTag"
            )
            Assert-ReleaseCommandSucceeded $pushResult '受 lease 保护地恢复稳定 Tag'
            return New-ReleaseActionResult $true '稳定 Tag 已按精确 lease 恢复'
        }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        throw '必须提供 -OutputPath；stdout 不作为机器接口'
    }

    $projectDir = Split-Path -Parent $PSScriptRoot
    $stateResult = $null
    $output = $null
    $exitCode = 0
    try {
        $stateResult = Get-ResolvedReleaseState `
            -ProjectDir $projectDir `
            -DeepVerifyRelease:($Stage -in @('VerifyRelease', 'Cleanup'))
        $message = "已从现场事实推导发布状态：$($stateResult.state)"

        switch ($Stage) {
            'Status' {
                if ($stateResult.state -eq 'Conflict') {
                    throw "发布现场冲突：$($stateResult.reasonCode)"
                }
            }
            'PushMain' {
                Invoke-ReleaseStageAction -Stage PushMain -StateResult $stateResult | Out-Null
                $stateResult = Get-ResolvedReleaseState -ProjectDir $projectDir
                if ($stateResult.state -ne 'MainPushed') {
                    throw "PushMain 后预期 MainPushed，实际为 $($stateResult.state)"
                }
                $message = 'main 已普通推送并从远端事实复核'
            }
            'PushTag' {
                Invoke-ReleaseStageAction -Stage PushTag -StateResult $stateResult | Out-Null
                $stateResult = Get-ResolvedReleaseState -ProjectDir $projectDir
                if ($stateResult.state -ne 'AwaitingTagActions') {
                    throw "PushTag 后预期 AwaitingTagActions，实际为 $($stateResult.state)"
                }
                $message = '稳定 annotated Tag 已普通推送并复核 peeled SHA'
            }
            'WaitTagActions' {
                $stateResult = Invoke-ReleaseWorkflowWait `
                    -ProjectDir $projectDir `
                    -DiscoveryTimeoutMinutes $DiscoveryTimeoutMinutes `
                    -CompletionTimeoutMinutes $CompletionTimeoutMinutes `
                    -PollSeconds $PollSeconds
                if ($stateResult.state -ne 'TagActionsSucceeded') {
                    throw "Tag Actions 未进入成功状态：$($stateResult.state)"
                }
                $message = '目标 Tag 最新 Actions attempt 与唯一 Artifact 已深度核验'
            }
            'PushRecoveryMain' {
                if ($stateResult.state -ne 'RecoveryPrepared') {
                    throw "PushRecoveryMain 只接受 RecoveryPrepared，实际为 $($stateResult.state)"
                }
                if ([string]::IsNullOrWhiteSpace($RecoveryEvidencePath) -or
                    [System.IO.Path]::GetFullPath($RecoveryEvidencePath) -cne
                        [System.IO.Path]::GetFullPath([string] $stateResult.recovery.gateEvidencePath)) {
                    throw 'PushRecoveryMain 的恢复证据路径与现场事实不一致'
                }
                Invoke-ReleaseStageAction -Stage PushRecoveryMain -StateResult $stateResult | Out-Null
                $stateResult = Get-ResolvedReleaseState -ProjectDir $projectDir
                if ($stateResult.state -ne 'TagRecoveryRequired') {
                    throw "PushRecoveryMain 后预期 TagRecoveryRequired，实际为 $($stateResult.state)"
                }
                $message = '同版本恢复候选已普通快进推送，稳定 Tag 尚未移动'
            }
            'RecoverTag' {
                if ([string]::IsNullOrWhiteSpace($RecoveryEvidencePath) -or
                    [System.IO.Path]::GetFullPath($RecoveryEvidencePath) -cne
                        [System.IO.Path]::GetFullPath([string] $stateResult.recovery.gateEvidencePath)) {
                    throw 'RecoverTag 的恢复证据路径与现场事实不一致'
                }
                Invoke-ReleaseStageAction `
                    -Stage RecoverTag `
                    -StateResult $stateResult `
                    -ExpectedOldTagObjectId $ExpectedOldTagObjectId `
                    -ExpectedOldTagPeeledSha $ExpectedOldTagPeeledSha `
                    -NewTargetSha $NewTargetSha `
                    -ExpectedRecoveryEvidenceSha256 $ExpectedRecoveryEvidenceSha256 `
                    -ConfirmTagRecovery $ConfirmTagRecovery | Out-Null
                $stateResult = Get-ResolvedReleaseState -ProjectDir $projectDir
                if ($stateResult.state -ne 'AwaitingTagActions') {
                    throw "RecoverTag 后预期 AwaitingTagActions，实际为 $($stateResult.state)"
                }
                $message = '稳定 Tag 已按 annotated tag object lease 恢复并复核'
            }
            'PublishRelease' {
                Invoke-ReleasePublication -ProjectDir $projectDir -StateResult $stateResult | Out-Null
                $stateResult = Get-ResolvedReleaseState -ProjectDir $projectDir
                if ($stateResult.state -notin @('ReleasePublished', 'Completed')) {
                    throw "PublishRelease 后状态错误：$($stateResult.state)"
                }
                $message = '正式 GitHub Release 已创建或补全，等待最终深度复核'
            }
            'VerifyRelease' {
                if ($stateResult.state -ne 'Completed') {
                    throw "正式发布未完成：$($stateResult.state)"
                }
                $message = 'latest Release、目标 Tag run Artifact 与唯一 APK 已深度复核一致'
            }
            'Cleanup' {
                if ($stateResult.state -ne 'Completed') {
                    throw "Cleanup 只接受 Completed，实际为 $($stateResult.state)"
                }
                foreach ($path in @(
                    (Join-Path $projectDir 'app/build/reports/release-artifacts'),
                    (Join-Path $projectDir 'app/build/reports/release-assets'),
                    (Join-Path $projectDir 'app/build/reports/release-notes.md')
                )) {
                    Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
                }
                $message = '临时下载与 Release notes 已清理，状态仍为 Completed'
            }
        }

        $output = New-ReleaseMachineOutput `
            -Stage $Stage `
            -StateResult $stateResult `
            -Ok $true `
            -Message $message
    } catch {
        $exitCode = 1
        if ($null -eq $stateResult) {
            $stateResult = [pscustomobject]@{
                state = 'Conflict'
                reasonCode = 'STATUS_COLLECTION_FAILED'
                targetVersion = ''
                targetVersionCode = 0
                targetTag = ''
                targetSha = $null
                baselineTag = ''
                cacheStatus = 'Missing'
                tag = [pscustomobject]@{}
                run = [pscustomobject]@{}
                artifact = [pscustomobject]@{}
                release = [pscustomobject]@{}
                recovery = [pscustomobject]@{}
                transition = 'Stop'
            }
        }
        $output = New-ReleaseMachineOutput `
            -Stage $Stage `
            -StateResult $stateResult `
            -Ok $false `
            -Message $_.Exception.Message
    }

    Write-ReleaseOutputs -ProjectDir $projectDir -OutputPath $OutputPath -Output $output
    Write-Host "$($output.stage): state=$($output.state) reason=$($output.reasonCode) ok=$($output.ok)"
    exit $exitCode
}
