Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'release-state.ps1')
. (Join-Path $PSScriptRoot 'release-verify-apk.ps1')
. (Join-Path $PSScriptRoot 'release-publish.ps1')
. (Join-Path $PSScriptRoot 'release-prepare.ps1')

$script:Passed = 0
$script:Failed = 0

function Invoke-ReleaseTest {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $Body
    )

    try {
        & $Body
        $script:Passed++
        Write-Host "PASS $Name"
    } catch {
        $script:Failed++
        Write-Host "FAIL $Name - $($_.Exception.Message)"
    }
}

function Assert-ReleaseEqual {
    param(
        [AllowNull()] [object] $Actual,
        [AllowNull()] [object] $Expected,
        [string] $Message = '值不相等'
    )

    if ($Actual -cne $Expected) {
        throw "$Message：expected=[$Expected] actual=[$Actual]"
    }
}

function Assert-ReleaseNull {
    param(
        [AllowNull()] [object] $Actual,
        [string] $Message = '值应为空'
    )

    if ($null -ne $Actual) {
        throw "$Message：actual=[$Actual]"
    }
}

function New-TestVersion {
    param([string] $Name, [int] $Code)
    return [pscustomobject]@{ versionName = $Name; versionCode = $Code }
}

function New-TestFacts {
    param(
        [string] $WorktreeVersion = '1.8.11',
        [int] $WorktreeCode = 156,
        [string] $HeadVersion = '1.8.11',
        [int] $HeadCode = 156,
        [string] $OriginVersion = '1.8.11',
        [int] $OriginCode = 156,
        [bool] $Clean = $true,
        [string[]] $DirtyPaths = @()
    )

    return [pscustomobject]@{
        baseline = [pscustomobject]@{ tag = 'v1.8.11'; versionName = '1.8.11'; versionCode = 156; sha = ('a' * 40) }
        repository = [pscustomobject]@{
            branch = 'main'
            headSha = ('c' * 40)
            originMainSha = ('b' * 40)
            headIsOriginMainDescendant = $true
            clean = $Clean
            dirtyPaths = $DirtyPaths
        }
        versions = [pscustomobject]@{
            worktree = New-TestVersion $WorktreeVersion $WorktreeCode
            head = New-TestVersion $HeadVersion $HeadCode
            originMain = New-TestVersion $OriginVersion $OriginCode
        }
        toolchain = [pscustomobject]@{ valid = $true }
        remoteTag = [pscustomobject]@{ exists = $false }
        tagRun = [pscustomobject]@{ found = $false }
        artifact = [pscustomobject]@{}
        release = [pscustomobject]@{ exists = $false }
        recovery = [pscustomobject]@{ gateEvidenceStatus = 'Missing' }
        cacheStatus = 'Missing'
        conflicts = @()
    }
}

function Set-TestRemoteTag {
    param([object] $Facts)
    $Facts.remoteTag = [pscustomobject]@{
        exists = $true
        tagName = 'v1.9.0'
        versionName = '1.9.0'
        versionCode = 157
        objectId = ('d' * 40)
        peeledSha = ('c' * 40)
    }
    $Facts.versions.worktree = New-TestVersion '1.9.0' 157
    $Facts.versions.head = New-TestVersion '1.9.0' 157
    $Facts.versions.originMain = New-TestVersion '1.9.0' 157
    $Facts.repository.originMainSha = ('c' * 40)
}

function Set-TestTagRun {
    param([object] $Facts, [string] $Conclusion, [string] $Status = 'completed')
    $Facts.tagRun = [pscustomobject]@{
        found = $true
        workflow = 'android-benchmark.yml'
        event = 'push'
        headBranch = 'v1.9.0'
        headSha = ('c' * 40)
        status = $Status
        conclusion = $Conclusion
        databaseId = 42
        attempt = 1
    }
}

function New-TestApkFixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-apk-test-$([guid]::NewGuid())"
    $tools = Join-Path $root 'build-tools/36.0.0'
    New-Item -ItemType Directory -Path $tools -Force | Out-Null
    $aapt2 = Join-Path $tools 'aapt2'
    $apksigner = Join-Path $tools 'apksigner'
    $apk = Join-Path $root '2026-07-16T12-34-56.apk'
    [System.IO.File]::WriteAllText($aapt2, '')
    [System.IO.File]::WriteAllText($apksigner, '')
    [System.IO.File]::WriteAllText($apk, 'apk-bytes')
    return [pscustomobject]@{ root = $root; apk = $apk; aapt2 = $aapt2; apksigner = $apksigner }
}

function New-TestRecoveryEvidence {
    param(
        [string] $CandidateSha = ('e' * 40),
        [string] $OldTagObjectId = ('d' * 40),
        [string] $OldTagPeeledSha = ('c' * 40)
    )

    $steps = @(
        Get-ReleaseGateStepDefinitions | ForEach-Object {
            [pscustomobject]@{
                id = [string] $_.id
                exitCode = 0
                status = if ($_.id -eq 'deviceReleaseExtensions') { 'SKIPPED_NO_DEVICE' } else { 'PASSED' }
                startedAt = '2026-07-16T12:00:00+00:00'
                completedAt = '2026-07-16T12:01:00+00:00'
            }
        }
    )
    $evidence = [pscustomobject][ordered]@{
        schemaVersion = 1
        kind = 'same-version-tag-recovery-local-gate'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetVersionCode = 157
        candidateSha = $CandidateSha
        oldTagObjectId = $OldTagObjectId
        oldTagPeeledSha = $OldTagPeeledSha
        prePushOriginMainSha = $OldTagPeeledSha
        repository = [pscustomobject]@{
            branchBefore = 'main'
            headBefore = $CandidateSha
            worktreeCleanBefore = $true
            indexCleanBefore = $true
            originMainBefore = $OldTagPeeledSha
            tagObjectBefore = $OldTagObjectId
            tagPeeledBefore = $OldTagPeeledSha
            branchAfter = 'main'
            headAfter = $CandidateSha
            worktreeCleanAfter = $true
            indexCleanAfter = $true
            originMainAfter = $OldTagPeeledSha
            tagObjectAfter = $OldTagObjectId
            tagPeeledAfter = $OldTagPeeledSha
        }
        failedRun = [pscustomobject]@{
            workflow = 'android-benchmark.yml'
            event = 'push'
            headBranch = 'v1.9.0'
            headSha = $OldTagPeeledSha
            databaseId = 42
            attempt = 1
            status = 'completed'
            conclusion = 'failure'
            completedAt = '2026-07-16T11:00:00+00:00'
        }
        gate = [pscustomobject]@{
            profile = 'full-release'
            startedAt = '2026-07-16T12:00:00+00:00'
            completedAt = '2026-07-16T12:30:00+00:00'
            steps = $steps
        }
        artifact = [pscustomobject]@{
            localPath = '/tmp/2026-07-16T12-29-00.apk'
            fileName = '2026-07-16T12-29-00.apk'
            packageName = 'cc.pscly.onememos'
            versionName = '1.9.0'
            versionCode = 157
            sha256 = ('1' * 64)
            signerSha256 = '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
            verifiedAt = '2026-07-16T12:29:00+00:00'
        }
        walkthrough = [pscustomobject]@{
            checklistId = 'task35-release-walkthrough-v1'
            candidateSha = $CandidateSha
            apkSha256 = ('1' * 64)
            completed = $true
            confirmedAt = '2026-07-16T12:40:00+00:00'
        }
        createdAt = '2026-07-16T12:41:00+00:00'
        payloadSha256 = $null
    }
    $evidence.payloadSha256 = Get-RecoveryPayloadSha256 -Evidence $evidence
    return $evidence
}

function New-TestRecoveryState {
    param(
        [ValidateSet('RecoveryCandidate', 'RecoveryPrepared')]
        [string] $State,
        [Parameter(Mandatory)] [string] $EvidencePath
    )

    return [pscustomobject]@{
        state = $State
        reasonCode = if ($State -eq 'RecoveryPrepared') { 'RECOVERY_GATE_VALID' } else { 'RECOVERY_GATE_REQUIRED' }
        targetVersion = '1.9.0'
        targetVersionCode = 157
        targetTag = 'v1.9.0'
        targetSha = ('c' * 40)
        baselineTag = 'v1.8.11'
        cacheStatus = 'Missing'
        tag = [pscustomobject]@{ exists = $true; objectId = ('d' * 40); peeledSha = ('c' * 40) }
        run = [pscustomobject]@{
            found = $true
            workflow = 'android-benchmark.yml'
            event = 'push'
            headBranch = 'v1.9.0'
            headSha = ('c' * 40)
            databaseId = 42
            attempt = 1
            status = 'completed'
            conclusion = 'failure'
            completedAt = '2026-07-16T11:00:00+00:00'
        }
        artifact = [pscustomobject]@{}
        release = [pscustomobject]@{ exists = $false }
        recovery = [pscustomobject]@{
            oldTagObjectId = ('d' * 40)
            oldTagPeeledSha = ('c' * 40)
            candidateSha = ('e' * 40)
            prePushOriginMainSha = ('c' * 40)
            gateEvidencePath = $EvidencePath
            gateEvidenceSha256 = $null
            gateEvidenceStatus = if ($State -eq 'RecoveryPrepared') { 'Valid' } else { 'Missing' }
            authorizationRequired = $false
            invalidatedSha = $null
        }
        transition = if ($State -eq 'RecoveryPrepared') { 'PushRecoveryMain' } else { 'PrepareRecovery' }
    }
}

Invoke-ReleaseTest '特殊版本从 1.8.11 推导 1.9.0' {
    Assert-ReleaseEqual (Get-NextStableVersion '1.8.11') '1.9.0'
}
Invoke-ReleaseTest '通用版本按 minor 递增' {
    Assert-ReleaseEqual (Get-NextStableVersion '2.4.7') '2.5.0'
}
Invoke-ReleaseTest 'NewRelease' {
    $state = Resolve-ReleaseState (New-TestFacts)
    Assert-ReleaseEqual $state.state 'NewRelease'
    Assert-ReleaseEqual $state.targetVersion '1.9.0'
    Assert-ReleaseEqual $state.targetVersionCode 157
    Assert-ReleaseNull $state.targetSha
}
Invoke-ReleaseTest 'PreparedWorktree 不二次递增' {
    $facts = New-TestFacts -WorktreeVersion '1.9.0' -WorktreeCode 157 -Clean $false -DirtyPaths @('app/build.gradle.kts')
    $state = Resolve-ReleaseState $facts
    Assert-ReleaseEqual $state.state 'PreparedWorktree'
    Assert-ReleaseEqual $state.targetVersionCode 157
}
Invoke-ReleaseTest 'Prepared' {
    $facts = New-TestFacts -WorktreeVersion '1.9.0' -WorktreeCode 157 -HeadVersion '1.9.0' -HeadCode 157
    $state = Resolve-ReleaseState $facts
    Assert-ReleaseEqual $state.state 'Prepared'
    Assert-ReleaseEqual $state.targetSha ('c' * 40)
}
Invoke-ReleaseTest 'MainPushed' {
    $facts = New-TestFacts -WorktreeVersion '1.9.0' -WorktreeCode 157 -HeadVersion '1.9.0' -HeadCode 157 -OriginVersion '1.9.0' -OriginCode 157
    $facts.repository.originMainSha = ('c' * 40)
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'MainPushed'
}
Invoke-ReleaseTest 'AwaitingTagActions' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'AwaitingTagActions'
}
Invoke-ReleaseTest 'TagActionsFailed' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'failure'
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'TagActionsFailed'
}
Invoke-ReleaseTest 'RecoveryCandidate' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'failure'
    $facts.repository.headSha = ('e' * 40)
    $facts.repository.originMainSha = ('c' * 40)
    $facts.recovery = [pscustomobject]@{ candidateSha = ('e' * 40); gateEvidenceStatus = 'Missing' }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'RecoveryCandidate'
}
Invoke-ReleaseTest 'RecoveryPrepared' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'failure'
    $facts.repository.headSha = ('e' * 40)
    $facts.recovery = [pscustomobject]@{ candidateSha = ('e' * 40); gateEvidenceStatus = 'Valid' }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'RecoveryPrepared'
}
Invoke-ReleaseTest 'TagRecoveryRequired' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'failure'
    $facts.repository.headSha = ('e' * 40)
    $facts.repository.originMainSha = ('e' * 40)
    $facts.recovery = [pscustomobject]@{ candidateSha = ('e' * 40); gateEvidenceStatus = 'Valid' }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'TagRecoveryRequired'
}
Invoke-ReleaseTest '恢复 main 已推但证据缺失进入 Conflict' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'failure'
    $facts.repository.originMainSha = ('e' * 40)
    $facts.recovery = [pscustomobject]@{ candidateSha = ('e' * 40); gateEvidenceStatus = 'Missing' }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'Conflict'
}
Invoke-ReleaseTest 'TagArtifactUnavailable' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.artifact = [pscustomobject]@{ count = 0; verified = $false }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'TagArtifactUnavailable'
}
Invoke-ReleaseTest 'TagActionsSucceeded' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.artifact = [pscustomobject]@{ count = 1; expired = $false; nameMatches = $true; versionMatches = $true; verified = $true; sha256 = ('1' * 64) }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'TagActionsSucceeded'
}
Invoke-ReleaseTest 'ReleaseIncomplete' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.artifact = [pscustomobject]@{ count = 1; expired = $false; nameMatches = $true; versionMatches = $true; verified = $true; sha256 = ('1' * 64) }
    $facts.release = [pscustomobject]@{ exists = $true; draft = $false; prerelease = $false; tagName = 'v1.9.0'; apkCount = 0 }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'ReleaseIncomplete'
}
Invoke-ReleaseTest 'ReleasePublished' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.artifact = [pscustomobject]@{ count = 1; expired = $false; nameMatches = $true; versionMatches = $true; verified = $true; sha256 = ('1' * 64) }
    $facts.release = [pscustomobject]@{ exists = $true; draft = $false; prerelease = $false; tagName = 'v1.9.0'; apkCount = 1; deepVerified = $false }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'ReleasePublished'
}
Invoke-ReleaseTest 'Completed' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.artifact = [pscustomobject]@{ count = 1; expired = $false; nameMatches = $true; versionMatches = $true; verified = $true; sha256 = ('1' * 64) }
    $facts.release = [pscustomobject]@{ exists = $true; draft = $false; prerelease = $false; tagName = 'v1.9.0'; apkCount = 1; deepVerified = $true; isLatest = $true; apkSha256 = ('1' * 64) }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'Completed'
}
Invoke-ReleaseTest '已有 Release 不能绕过 Tag run 与 Artifact 门禁' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    $facts.release = [pscustomobject]@{ exists = $true; draft = $false; prerelease = $false; tagName = 'v1.9.0'; apkCount = 1; deepVerified = $false }
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'Conflict'
}
Invoke-ReleaseTest '工具链漂移进入 Conflict' {
    $facts = New-TestFacts
    $facts.toolchain.valid = $false
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'Conflict'
}
Invoke-ReleaseTest '缓存状态不参与版本推导' {
    foreach ($cache in @('Missing', 'Consistent', 'Stale')) {
        $facts = New-TestFacts
        $facts.cacheStatus = $cache
        $state = Resolve-ReleaseState $facts
        Assert-ReleaseEqual $state.state 'NewRelease'
        Assert-ReleaseEqual $state.targetVersion '1.9.0'
    }
}
Invoke-ReleaseTest 'Tag run 必须精确匹配 Tag 和 SHA' {
    $facts = New-TestFacts
    Set-TestRemoteTag $facts
    Set-TestTagRun $facts 'success'
    $facts.tagRun.headBranch = 'main'
    Assert-ReleaseEqual (Resolve-ReleaseState $facts).state 'Conflict'
}
Invoke-ReleaseTest '状态转换白名单完整' {
    $expected = @(
        'NewRelease', 'PreparedWorktree', 'Prepared', 'MainPushed', 'AwaitingTagActions',
        'TagActionsFailed', 'RecoveryCandidate', 'RecoveryPrepared', 'TagRecoveryRequired',
        'TagArtifactUnavailable', 'TagActionsSucceeded', 'ReleaseIncomplete', 'ReleasePublished',
        'Completed', 'Conflict'
    )
    foreach ($state in $expected) {
        if ([string]::IsNullOrWhiteSpace((Get-ReleaseTransition $state))) {
            throw "状态缺少转换：$state"
        }
    }
}
Invoke-ReleaseTest 'APK 核验固定 Build Tools 36 并返回绑定元数据' {
    $fixture = New-TestApkFixture
    try {
        $calls = [System.Collections.Generic.List[object]]::new()
        $runner = {
            param([string] $FilePath, [string[]] $Arguments)
            $calls.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
            if ($FilePath -eq $fixture.aapt2) {
                return [pscustomobject]@{
                    exitCode = 0
                    output = "package: name='cc.pscly.onememos' versionCode='157' versionName='1.9.0'"
                }
            }
            return [pscustomobject]@{
                exitCode = 0
                output = 'Signer #1 certificate SHA-256 digest: 58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
            }
        }

        $result = Invoke-ReleaseApkVerification `
            -ApkPath $fixture.apk `
            -ExpectedVersionName '1.9.0' `
            -ExpectedVersionCode 157 `
            -AndroidHome $fixture.root `
            -CommandRunner $runner

        Assert-ReleaseEqual $result.packageName 'cc.pscly.onememos'
        Assert-ReleaseEqual $result.versionName '1.9.0'
        Assert-ReleaseEqual $result.versionCode 157
        Assert-ReleaseEqual $result.signerSha256 '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
        Assert-ReleaseEqual $result.sha256 ((Get-FileHash -LiteralPath $fixture.apk -Algorithm SHA256).Hash.ToLowerInvariant())
        Assert-ReleaseEqual $calls.Count 2
        Assert-ReleaseEqual $calls[0].filePath $fixture.aapt2
        Assert-ReleaseEqual $calls[1].filePath $fixture.apksigner
    } finally {
        Remove-Item -LiteralPath $fixture.root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest 'APK 核验拒绝多个签名证书' {
    $fixture = New-TestApkFixture
    try {
        $runner = {
            param([string] $FilePath, [string[]] $Arguments)
            if ($FilePath -eq $fixture.aapt2) {
                return [pscustomobject]@{
                    exitCode = 0
                    output = "package: name='cc.pscly.onememos' versionCode='157' versionName='1.9.0'"
                }
            }
            return [pscustomobject]@{
                exitCode = 0
                output = @(
                    'Signer #1 certificate SHA-256 digest: 58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e',
                    'Signer #2 certificate SHA-256 digest: 1111111111111111111111111111111111111111111111111111111111111111'
                ) -join [Environment]::NewLine
            }
        }

        $thrown = $false
        try {
            Invoke-ReleaseApkVerification `
                -ApkPath $fixture.apk `
                -ExpectedVersionName '1.9.0' `
                -ExpectedVersionCode 157 `
                -AndroidHome $fixture.root `
                -CommandRunner $runner | Out-Null
        } catch {
            $thrown = $true
        }
        Assert-ReleaseEqual $thrown $true '多个证书必须拒绝'
    } finally {
        Remove-Item -LiteralPath $fixture.root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest 'PushMain 只执行普通 main 推送' {
    $commands = [System.Collections.Generic.List[object]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $state = [pscustomobject]@{
        state = 'Prepared'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = ('c' * 40)
        recovery = [pscustomobject]@{}
    }

    $result = Invoke-ReleaseStageAction -Stage PushMain -StateResult $state -CommandRunner $runner

    Assert-ReleaseEqual $result.actionPerformed $true
    Assert-ReleaseEqual $commands.Count 1
    Assert-ReleaseEqual $commands[0].filePath 'git'
    Assert-ReleaseEqual ($commands[0].arguments -join ' ') 'push origin refs/heads/main:refs/heads/main'
}
Invoke-ReleaseTest 'PushMain 已进入后续状态时不重复推送' {
    $calls = 0
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $script:calls++
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $state = [pscustomobject]@{
        state = 'MainPushed'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = ('c' * 40)
        recovery = [pscustomobject]@{}
    }

    $result = Invoke-ReleaseStageAction -Stage PushMain -StateResult $state -CommandRunner $runner

    Assert-ReleaseEqual $result.actionPerformed $false
    Assert-ReleaseEqual $calls 0
}
Invoke-ReleaseTest 'PushTag 创建 annotated Tag 并普通推送' {
    $commands = [System.Collections.Generic.List[object]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $state = [pscustomobject]@{
        state = 'MainPushed'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = ('c' * 40)
        recovery = [pscustomobject]@{}
    }

    Invoke-ReleaseStageAction -Stage PushTag -StateResult $state -CommandRunner $runner | Out-Null

    Assert-ReleaseEqual $commands.Count 3
    Assert-ReleaseEqual ($commands[0].arguments -join ' ') 'for-each-ref --format=%(objecttype)%09%(*objectname)%09%(contents:subject) refs/tags/v1.9.0'
    Assert-ReleaseEqual ($commands[1].arguments -join ' ') "tag -a v1.9.0 $('c' * 40) -m 1memos 1.9.0"
    Assert-ReleaseEqual ($commands[2].arguments -join ' ') 'push origin refs/tags/v1.9.0:refs/tags/v1.9.0'
}
Invoke-ReleaseTest 'PushTag 中断重跑复用严格匹配的本地 annotated Tag' {
    $commands = [System.Collections.Generic.List[object]]::new()
    $targetSha = 'c' * 40
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        if ($Arguments[0] -eq 'for-each-ref') {
            return [pscustomobject]@{
                exitCode = 0
                output = "tag`t$targetSha`t1memos 1.9.0"
            }
        }
        if ($Arguments[0] -eq 'tag') {
            return [pscustomobject]@{ exitCode = 128; output = 'tag already exists' }
        }
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $state = [pscustomobject]@{
        state = 'MainPushed'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = $targetSha
        recovery = [pscustomobject]@{}
    }

    Invoke-ReleaseStageAction -Stage PushTag -StateResult $state -CommandRunner $runner | Out-Null

    Assert-ReleaseEqual $commands.Count 2
    Assert-ReleaseEqual ($commands[0].arguments -join ' ') 'for-each-ref --format=%(objecttype)%09%(*objectname)%09%(contents:subject) refs/tags/v1.9.0'
    Assert-ReleaseEqual ($commands[1].arguments -join ' ') 'push origin refs/tags/v1.9.0:refs/tags/v1.9.0'
}
Invoke-ReleaseTest 'PushTag 拒绝漂移的本地同名 Tag 且不推送' {
    $commands = [System.Collections.Generic.List[object]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        return [pscustomobject]@{
            exitCode = 0
            output = "tag`t$('d' * 40)`t1memos 1.9.0"
        }
    }
    $state = [pscustomobject]@{
        state = 'MainPushed'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = ('c' * 40)
        recovery = [pscustomobject]@{}
    }

    $thrown = $false
    try {
        Invoke-ReleaseStageAction -Stage PushTag -StateResult $state -CommandRunner $runner | Out-Null
    } catch {
        $thrown = $true
    }

    Assert-ReleaseEqual $thrown $true
    Assert-ReleaseEqual $commands.Count 1
    Assert-ReleaseEqual $commands[0].arguments[0] 'for-each-ref'
}
Invoke-ReleaseTest 'RecoverTag 只使用 annotated tag object lease' {
    $commands = [System.Collections.Generic.List[object]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $oldObject = 'd' * 40
    $oldPeeled = 'c' * 40
    $candidate = 'e' * 40
    $evidenceSha = '1' * 64
    $state = [pscustomobject]@{
        state = 'TagRecoveryRequired'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = $oldPeeled
        recovery = [pscustomobject]@{
            oldTagObjectId = $oldObject
            oldTagPeeledSha = $oldPeeled
            candidateSha = $candidate
            gateEvidenceSha256 = $evidenceSha
        }
    }
    $confirmation = "RECOVER_TAG:v1.9.0:$oldObject`:$oldPeeled`:$candidate`:$evidenceSha"

    Invoke-ReleaseStageAction `
        -Stage RecoverTag `
        -StateResult $state `
        -ExpectedOldTagObjectId $oldObject `
        -ExpectedOldTagPeeledSha $oldPeeled `
        -NewTargetSha $candidate `
        -ExpectedRecoveryEvidenceSha256 $evidenceSha `
        -ConfirmTagRecovery $confirmation `
        -CommandRunner $runner | Out-Null

    Assert-ReleaseEqual $commands.Count 2
    Assert-ReleaseEqual ($commands[0].arguments -join ' ') "tag -f -a v1.9.0 $candidate -m 1memos 1.9.0"
    Assert-ReleaseEqual ($commands[1].arguments -join ' ') "push --force-with-lease=refs/tags/v1.9.0:$oldObject origin refs/tags/v1.9.0:refs/tags/v1.9.0"
}
Invoke-ReleaseTest 'RecoverTag 确认串错误时零副作用' {
    $calls = 0
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $script:calls++
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }
    $state = [pscustomobject]@{
        state = 'TagRecoveryRequired'
        targetTag = 'v1.9.0'
        targetVersion = '1.9.0'
        targetSha = ('c' * 40)
        recovery = [pscustomobject]@{
            oldTagObjectId = ('d' * 40)
            oldTagPeeledSha = ('c' * 40)
            candidateSha = ('e' * 40)
            gateEvidenceSha256 = ('1' * 64)
        }
    }
    $thrown = $false
    try {
        Invoke-ReleaseStageAction `
            -Stage RecoverTag `
            -StateResult $state `
            -ExpectedOldTagObjectId ('d' * 40) `
            -ExpectedOldTagPeeledSha ('c' * 40) `
            -NewTargetSha ('e' * 40) `
            -ExpectedRecoveryEvidenceSha256 ('1' * 64) `
            -ConfirmTagRecovery 'WRONG' `
            -CommandRunner $runner | Out-Null
    } catch {
        $thrown = $true
    }
    Assert-ReleaseEqual $thrown $true
    Assert-ReleaseEqual $calls 0
}
Invoke-ReleaseTest 'Gradle 版本解析要求唯一稳定值' {
    $text = @'
android {
    defaultConfig {
        versionCode = 157
        versionName = "1.9.0"
    }
}
'@
    $version = Get-ReleaseVersionFromGradleText -Text $text -Source 'fixture'
    Assert-ReleaseEqual $version.versionName '1.9.0'
    Assert-ReleaseEqual $version.versionCode 157
}
Invoke-ReleaseTest '机器 JSON 固定字段且使用 UTF-8 无 BOM 原子写入' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-json-test-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $path = Join-Path $root 'nested/result.json'
    try {
        $state = [pscustomobject]@{
            state = 'Prepared'
            reasonCode = 'RELEASE_COMMIT_READY'
            targetVersion = '1.9.0'
            targetVersionCode = 157
            targetTag = 'v1.9.0'
            targetSha = ('c' * 40)
            baselineTag = 'v1.8.11'
            cacheStatus = 'Missing'
            tag = [pscustomobject]@{ exists = $false; objectId = $null; peeledSha = $null }
            run = [pscustomobject]@{}
            artifact = [pscustomobject]@{}
            release = [pscustomobject]@{}
            recovery = [pscustomobject]@{}
            transition = 'PushMain'
        }
        $output = New-ReleaseMachineOutput -Stage Status -StateResult $state -Ok $true -Message 'ok'
        Write-ReleaseJsonAtomic -Path $path -Value $output

        $bytes = [System.IO.File]::ReadAllBytes($path)
        Assert-ReleaseEqual (($bytes[0..2] | ForEach-Object { $_.ToString('x2') }) -join '') '7b0a20'
        $decoded = Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json -Depth 64
        foreach ($name in @(
            'schemaVersion', 'ok', 'stage', 'state', 'reasonCode', 'message',
            'targetVersion', 'targetVersionCode', 'targetTag', 'targetSha', 'baselineTag',
            'cacheStatus', 'tag', 'run', 'artifact', 'release', 'recovery', 'transition', 'observedAt'
        )) {
            if ($null -eq $decoded.PSObject.Properties[$name]) {
                throw "机器 JSON 缺少字段：$name"
            }
        }
        Assert-ReleaseEqual $decoded.schemaVersion 1
        Assert-ReleaseEqual $decoded.state 'Prepared'
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest '稳定 Release 查询接受空冲突集合' {
    $conflicts = [System.Collections.Generic.List[string]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        return [pscustomobject]@{ exitCode = 0; output = '[]' }
    }

    $releases = @(
        Get-ReleaseStableReleases `
            -Repository 'pscly/xinliu_android' `
            -RemoteTags @{} `
            -Conflicts $conflicts `
            -CommandRunner $runner
    )

    Assert-ReleaseEqual $releases.Count 0
    Assert-ReleaseEqual $conflicts.Count 0
}
Invoke-ReleaseTest '缺少任一签名字段时准备门禁阻塞且不泄露路径' {
    $status = Get-ReleaseSigningStatus -Values @{
        ANDROID_RELEASE_KEYSTORE_PATH = '/secret/release.jks'
        ANDROID_RELEASE_STORE_PASSWORD = 'store-secret'
        ANDROID_RELEASE_KEY_ALIAS = ''
        ANDROID_RELEASE_KEY_PASSWORD = 'key-secret'
    }

    Assert-ReleaseEqual $status.available $false
    Assert-ReleaseEqual $status.reasonCode 'BLOCKED_SIGNING_MISSING'
    Assert-ReleaseEqual ($status.missing -join ',') 'ANDROID_RELEASE_KEY_ALIAS'
    if (($status | ConvertTo-Json -Depth 8) -match '/secret|store-secret|key-secret') {
        throw '签名状态不得泄露敏感值'
    }
}
Invoke-ReleaseTest 'Prepare 缺签名时版本与外部命令均保持不变' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-prepare-test-$([guid]::NewGuid())"
    $appDir = Join-Path $root 'app'
    New-Item -ItemType Directory -Path $appDir -Force | Out-Null
    $gradlePath = Join-Path $appDir 'build.gradle.kts'
    $baseline = @'
android {
    defaultConfig {
        versionCode = 156
        versionName = "1.8.11"
    }
}
'@
    [System.IO.File]::WriteAllText($gradlePath, $baseline, [System.Text.UTF8Encoding]::new($false))
    try {
        $commands = [System.Collections.Generic.List[object]]::new()
        $runner = {
            param([string] $FilePath, [string[]] $Arguments)
            $commands.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
            return [pscustomobject]@{ exitCode = 0; output = '' }
        }
        $state = Resolve-ReleaseState (New-TestFacts)
        $resolver = {
            param([string] $ProjectDir)
            return $state
        }

        $result = Invoke-ReleasePreparation `
            -Stage Prepare `
            -ProjectDir $root `
            -SigningValues @{
                ANDROID_RELEASE_KEYSTORE_PATH = ''
                ANDROID_RELEASE_STORE_PASSWORD = ''
                ANDROID_RELEASE_KEY_ALIAS = ''
                ANDROID_RELEASE_KEY_PASSWORD = ''
            } `
            -StateResolver $resolver `
            -CommandRunner $runner

        Assert-ReleaseEqual $result.ok $false
        Assert-ReleaseEqual $result.state 'NewRelease'
        Assert-ReleaseEqual $result.reasonCode 'BLOCKED_SIGNING_MISSING'
        Assert-ReleaseEqual $commands.Count 0
        Assert-ReleaseEqual ([System.IO.File]::ReadAllText($gradlePath)) $baseline
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest 'Prepare 只递增一次并按固定顺序执行完整门禁' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-prepare-green-$([guid]::NewGuid())"
    $appDir = Join-Path $root 'app'
    New-Item -ItemType Directory -Path $appDir -Force | Out-Null
    $gradlePath = Join-Path $appDir 'build.gradle.kts'
    $baseline = @'
android {
    defaultConfig {
        versionCode = 156
        versionName = "1.8.11"
    }
}
'@
    [System.IO.File]::WriteAllText($gradlePath, $baseline, [System.Text.UTF8Encoding]::new($false))
    try {
        $newRelease = Resolve-ReleaseState (New-TestFacts)
        $preparedFacts = New-TestFacts `
            -WorktreeVersion '1.9.0' `
            -WorktreeCode 157 `
            -Clean $false `
            -DirtyPaths @('app/build.gradle.kts')
        $preparedWorktree = Resolve-ReleaseState $preparedFacts
        $script:prepareResolveCalls = 0
        $resolver = {
            param([string] $ProjectDir)
            $script:prepareResolveCalls++
            if ($script:prepareResolveCalls -eq 1) { return $newRelease }
            return $preparedWorktree
        }
        $stepIds = [System.Collections.Generic.List[string]]::new()
        $stepRunner = {
            param([object] $Definition, [object] $Context)
            $stepIds.Add([string] $Definition.id)
            $artifact = if ($Definition.id -eq 'verifyApk') {
                [pscustomobject]@{
                    localPath = (Join-Path $root '2026-07-16T20-00-00.apk')
                    fileName = '2026-07-16T20-00-00.apk'
                    packageName = 'cc.pscly.onememos'
                    versionName = '1.9.0'
                    versionCode = 157
                    sha256 = ('1' * 64)
                    signerSha256 = '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
                }
            } else { $null }
            return [pscustomobject]@{
                id = [string] $Definition.id
                exitCode = 0
                status = if ($Definition.id -eq 'deviceReleaseExtensions') { 'SKIPPED_NO_DEVICE' } else { 'PASSED' }
                artifact = $artifact
            }
        }
        $signing = @{
            ANDROID_RELEASE_KEYSTORE_PATH = '/secret/release.jks'
            ANDROID_RELEASE_STORE_PASSWORD = 'store-secret'
            ANDROID_RELEASE_KEY_ALIAS = 'release'
            ANDROID_RELEASE_KEY_PASSWORD = 'key-secret'
        }

        $first = Invoke-ReleasePreparation `
            -Stage Prepare `
            -ProjectDir $root `
            -SigningValues $signing `
            -StateResolver $resolver `
            -GateStepRunner $stepRunner
        $second = Invoke-ReleasePreparation `
            -Stage Prepare `
            -ProjectDir $root `
            -SigningValues $signing `
            -StateResolver { param([string] $ProjectDir) $preparedWorktree } `
            -GateStepRunner $stepRunner

        $version = Get-ReleaseVersionFromGradleText `
            -Text ([System.IO.File]::ReadAllText($gradlePath)) `
            -Source $gradlePath
        Assert-ReleaseEqual $first.ok $true
        Assert-ReleaseEqual $first.state 'PreparedWorktree'
        Assert-ReleaseEqual $second.ok $true
        Assert-ReleaseEqual $second.targetVersionCode 157
        Assert-ReleaseEqual $version.versionName '1.9.0'
        Assert-ReleaseEqual $version.versionCode 157
        Assert-ReleaseEqual ($stepIds -join ',') ((@(
            Get-ReleaseGateStepDefinitions | ForEach-Object { $_.id }
        ) * 2) -join ',')
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest '恢复证据不能用自身字段证明推送前 origin main' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-recovery-binding-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $path = Join-Path $root 'evidence.json'
    try {
        $evidence = New-TestRecoveryEvidence
        $evidence.prePushOriginMainSha = ('b' * 40)
        $evidence.repository.originMainBefore = ('b' * 40)
        $evidence.repository.originMainAfter = ('b' * 40)
        $evidence.payloadSha256 = Get-RecoveryPayloadSha256 -Evidence $evidence
        Write-ReleaseJsonAtomic -Path $path -Value $evidence
        $runner = {
            param([string] $FilePath, [string[]] $Arguments)
            return [pscustomobject]@{ exitCode = 0; output = $path }
        }

        $result = Get-ReleaseEvidence `
            -ProjectDir $root `
            -TargetTag 'v1.9.0' `
            -CandidateSha ('e' * 40) `
            -OldTagObjectId ('d' * 40) `
            -OldTagPeeledSha ('c' * 40) `
            -CurrentOriginMainSha ('c' * 40) `
            -TargetVersionCode 157 `
            -TargetVersion '1.9.0' `
            -CommandRunner $runner

        Assert-ReleaseEqual $result.gateEvidenceStatus 'Stale'
        Assert-ReleaseEqual $result.gateEvidenceReasonCode 'RECOVERY_BINDING_PREPUSHORIGINMAINSHA'
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest '恢复证据规范摘要跨 JSON 往返保持一致' {
    $evidence = New-TestRecoveryEvidence
    $before = Get-RecoveryPayloadSha256 -Evidence $evidence
    $roundTripped = $evidence | ConvertTo-Json -Depth 100 | ConvertFrom-Json -Depth 100
    $after = Get-RecoveryPayloadSha256 -Evidence $roundTripped

    Assert-ReleaseEqual $after $before
}
Invoke-ReleaseTest 'PrepareRecovery 先写 pending 且精确确认后原子生成最终证据' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-recovery-prepare-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $evidencePath = Join-Path $root 'evidence.json'
    $pendingPath = "$evidencePath.pending"
    try {
        $candidate = 'e' * 40
        $oldObject = 'd' * 40
        $oldPeeled = 'c' * 40
        $script:recoveryEvidencePath = $evidencePath
        $stateResolver = {
            param([string] $ProjectDir)
            if (Test-Path -LiteralPath $script:recoveryEvidencePath -PathType Leaf) {
                return New-TestRecoveryState -State RecoveryPrepared -EvidencePath $script:recoveryEvidencePath
            }
            return New-TestRecoveryState -State RecoveryCandidate -EvidencePath $script:recoveryEvidencePath
        }
        $snapshotResolver = {
            param([string] $ProjectDir, [object] $StateResult)
            return [pscustomobject]@{
                branch = 'main'
                headSha = ('e' * 40)
                worktreeClean = $true
                indexClean = $true
                originMainSha = ('c' * 40)
                tagObjectId = ('d' * 40)
                tagPeeledSha = ('c' * 40)
            }
        }
        $stepRunner = {
            param([object] $Definition, [object] $Context)
            $artifact = if ($Definition.id -eq 'verifyApk') {
                [pscustomobject]@{
                    localPath = (Join-Path $root '2026-07-16T20-00-00.apk')
                    fileName = '2026-07-16T20-00-00.apk'
                    packageName = 'cc.pscly.onememos'
                    versionName = '1.9.0'
                    versionCode = 157
                    sha256 = ('1' * 64)
                    signerSha256 = '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
                    verifiedAt = '2026-07-16T12:29:00+00:00'
                }
            } else { $null }
            return [pscustomobject]@{
                id = [string] $Definition.id
                exitCode = 0
                status = if ($Definition.id -eq 'deviceReleaseExtensions') { 'SKIPPED_NO_DEVICE' } else { 'PASSED' }
                startedAt = '2026-07-16T12:00:00+00:00'
                completedAt = '2026-07-16T12:01:00+00:00'
                artifact = $artifact
            }
        }
        $signing = @{
            ANDROID_RELEASE_KEYSTORE_PATH = '/secret/release.jks'
            ANDROID_RELEASE_STORE_PASSWORD = 'store-secret'
            ANDROID_RELEASE_KEY_ALIAS = 'release'
            ANDROID_RELEASE_KEY_PASSWORD = 'key-secret'
        }

        $pending = Invoke-ReleasePreparation `
            -Stage PrepareRecovery `
            -ProjectDir $root `
            -SigningValues $signing `
            -StateResolver $stateResolver `
            -RecoverySnapshotResolver $snapshotResolver `
            -GateStepRunner $stepRunner `
            -CandidateSha $candidate `
            -ExpectedOldTagObjectId $oldObject `
            -ExpectedOldTagPeeledSha $oldPeeled `
            -RecoveryEvidencePath $evidencePath

        Assert-ReleaseEqual $pending.ok $true
        Assert-ReleaseEqual $pending.state 'RecoveryCandidate'
        Assert-ReleaseEqual $pending.reasonCode 'RECOVERY_WALKTHROUGH_REQUIRED'
        Assert-ReleaseEqual (Test-Path -LiteralPath $pendingPath -PathType Leaf) $true
        Assert-ReleaseEqual (Test-Path -LiteralPath $evidencePath -PathType Leaf) $false

        $final = Invoke-ReleasePreparation `
            -Stage PrepareRecovery `
            -ProjectDir $root `
            -SigningValues $signing `
            -StateResolver $stateResolver `
            -RecoverySnapshotResolver $snapshotResolver `
            -GateStepRunner $stepRunner `
            -CandidateSha $candidate `
            -ExpectedOldTagObjectId $oldObject `
            -ExpectedOldTagPeeledSha $oldPeeled `
            -RecoveryEvidencePath $evidencePath `
            -FinalizeWalkthrough `
            -ConfirmWalkthrough "RECOVERY_WALKTHROUGH:$candidate`:$('1' * 64)"

        Assert-ReleaseEqual $final.ok $true
        Assert-ReleaseEqual $final.state 'RecoveryPrepared'
        Assert-ReleaseEqual (Test-Path -LiteralPath $pendingPath -PathType Leaf) $false
        Assert-ReleaseEqual (Test-Path -LiteralPath $evidencePath -PathType Leaf) $true
        $evidence = Get-Content -LiteralPath $evidencePath -Raw -Encoding utf8 | ConvertFrom-Json -Depth 100
        $validation = Test-RecoveryEvidence -Evidence $evidence -Expected ([pscustomobject]@{
            targetTag = 'v1.9.0'
            targetVersion = '1.9.0'
            targetVersionCode = 157
            candidateSha = $candidate
            oldTagObjectId = $oldObject
            oldTagPeeledSha = $oldPeeled
            prePushOriginMainSha = $oldPeeled
            signerSha256 = '58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e'
        })
        Assert-ReleaseEqual $validation.status 'Valid'
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Invoke-ReleaseTest '版本准备从基线原子改为唯一目标且重跑不二次递增' {
    $baseline = @'
android {
    defaultConfig {
        versionCode = 156
        versionName = "1.8.11"
    }
}
'@
    $prepared = Set-ReleaseVersionInGradleText `
        -Text $baseline `
        -CurrentVersionName '1.8.11' `
        -CurrentVersionCode 156 `
        -TargetVersionName '1.9.0' `
        -TargetVersionCode 157
    $repeated = Set-ReleaseVersionInGradleText `
        -Text $prepared `
        -CurrentVersionName '1.9.0' `
        -CurrentVersionCode 157 `
        -TargetVersionName '1.9.0' `
        -TargetVersionCode 157

    $version = Get-ReleaseVersionFromGradleText -Text $repeated -Source 'fixture'
    Assert-ReleaseEqual $version.versionName '1.9.0'
    Assert-ReleaseEqual $version.versionCode 157
    Assert-ReleaseEqual $repeated $prepared
}
Invoke-ReleaseTest '恢复证据门禁步骤 ID 完整且唯一' {
    $ids = @(Get-ReleaseGateStepDefinitions | ForEach-Object { $_.id })
    Assert-ReleaseEqual ($ids -join ',') (
        'verifyArchitecture,testDebugUnitTest,lint,assembleAppBenchmark,' +
        'assembleBaselineProfileBenchmark,assembleMacrobenchmarkBenchmark,verifyApk,deviceReleaseExtensions'
    )
    Assert-ReleaseEqual (@($ids | Sort-Object -Unique).Count) 8
}
Invoke-ReleaseTest 'DefaultReleaseGateStep gradle 选用平台对应的 gradlew 且参数完整' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-gradle-gate-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $script:defaultGradleGateCalls = [System.Collections.Generic.List[object]]::new()
    try {
        $runner = {
            param([string] $FilePath, [string[]] $Arguments)
            $script:defaultGradleGateCalls.Add([pscustomobject]@{
                filePath = $FilePath
                arguments = @($Arguments)
            })
            return [pscustomobject]@{ exitCode = 0; output = '' }
        }
        $definition = [pscustomobject]@{
            id = 'testDebugUnitTest'
            kind = 'gradle'
            task = 'testDebugUnitTest'
        }
        $context = [pscustomobject]@{
            projectDir = $root
            commandRunner = $runner
            targetVersion = '1.9.0'
            targetVersionCode = 157
        }

        $result = Invoke-DefaultReleaseGateStep -Definition $definition -Context $context
        $wrapper = if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }

        Assert-ReleaseEqual $result.id 'testDebugUnitTest'
        Assert-ReleaseEqual $result.exitCode 0
        Assert-ReleaseEqual $result.status 'PASSED'
        Assert-ReleaseEqual $script:defaultGradleGateCalls.Count 1
        Assert-ReleaseEqual $script:defaultGradleGateCalls[0].filePath (Join-Path $root $wrapper)
        Assert-ReleaseEqual (
            $script:defaultGradleGateCalls[0].arguments -join ' '
        ) 'testDebugUnitTest -Pkotlin.compiler.execution.strategy=in-process --stacktrace'
    } finally {
        Remove-Variable -Name defaultGradleGateCalls -Scope Script -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# --- v1.13.0 Cleanup 目标选择与删除范围回归测试 ---

Invoke-ReleaseTest 'Contract A：Cleanup Pin 选择最新稳定版 1.13.0 为目标、前一版 1.9.0 为基线且验证 HEAD/origin 为后代' {
    $stableReleases = @(
        [pscustomobject]@{
            tagName = 'v1.9.0'
            versionName = '1.9.0'
            versionCode = 157
            major = 1; minor = 9; patch = 0
            objectId = ('b' * 40)
            peeledSha = ('b' * 40)
        },
        [pscustomobject]@{
            tagName = 'v1.13.0'
            versionName = '1.13.0'
            versionCode = 163
            major = 1; minor = 13; patch = 0
            objectId = ('d' * 40)
            peeledSha = ('d' * 40)
        }
    )
    $remoteTags = @{
        'v1.9.0' = [pscustomobject]@{ objectId = ('b' * 40); peeledSha = ('b' * 40) }
        'v1.13.0' = [pscustomobject]@{ objectId = ('d' * 40); peeledSha = ('d' * 40) }
    }
    $conflicts = [System.Collections.Generic.List[string]]::new()
    $gitCalls = [System.Collections.Generic.List[object]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        $gitCalls.Add([pscustomobject]@{ filePath = $FilePath; arguments = @($Arguments) })
        if ($FilePath -eq 'git' -and $Arguments[0] -eq 'merge-base' -and $Arguments[1] -eq '--is-ancestor') {
            return [pscustomobject]@{ exitCode = 0; output = '' }
        }
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }

    $selection = Get-ReleaseTargetSelection `
        -StableReleases $stableReleases `
        -RemoteTags $remoteTags `
        -WorktreeVersion (New-TestVersion '1.13.0' 163) `
        -HeadVersion (New-TestVersion '1.13.0' 163) `
        -OriginVersion (New-TestVersion '1.13.0' 163) `
        -HeadSha ('e' * 40) `
        -OriginMainSha ('e' * 40) `
        -Conflicts $conflicts `
        -PinLatestStableTarget `
        -CommandRunner $runner

    Assert-ReleaseEqual $selection.targetVersion '1.13.0'
    Assert-ReleaseEqual $selection.baselineRelease.versionName '1.9.0'
    Assert-ReleaseEqual $selection.baselineRelease.versionCode 157
    Assert-ReleaseEqual $conflicts.Count 0

    $mergeBaseCalls = @($gitCalls | Where-Object {
        $_.filePath -eq 'git' -and $_.arguments[0] -eq 'merge-base' -and $_.arguments[1] -eq '--is-ancestor'
    })
    Assert-ReleaseEqual $mergeBaseCalls.Count 2 'merge-base --is-ancestor 应为 HEAD 和 origin 各检查一次'
    foreach ($call in $mergeBaseCalls) {
        Assert-ReleaseEqual $call.arguments[2] ('d' * 40) '祖先应为目标 Tag 提交'
        Assert-ReleaseEqual $call.arguments[3] ('e' * 40) '后代应为 HEAD 或 origin'
    }
}

Invoke-ReleaseTest 'Contract B：无 Pin 时保持通用选择语义、基线为最新稳定版 1.13.0、下一版为 1.14.0' {
    $stableReleases = @(
        [pscustomobject]@{
            tagName = 'v1.9.0'
            versionName = '1.9.0'
            versionCode = 157
            major = 1; minor = 9; patch = 0
            objectId = ('b' * 40)
            peeledSha = ('b' * 40)
        },
        [pscustomobject]@{
            tagName = 'v1.13.0'
            versionName = '1.13.0'
            versionCode = 163
            major = 1; minor = 13; patch = 0
            objectId = ('d' * 40)
            peeledSha = ('d' * 40)
        }
    )
    $remoteTags = @{
        'v1.9.0' = [pscustomobject]@{ objectId = ('b' * 40); peeledSha = ('b' * 40) }
        'v1.13.0' = [pscustomobject]@{ objectId = ('d' * 40); peeledSha = ('d' * 40) }
    }
    $conflicts = [System.Collections.Generic.List[string]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }

    $selection = Get-ReleaseTargetSelection `
        -StableReleases $stableReleases `
        -RemoteTags $remoteTags `
        -WorktreeVersion (New-TestVersion '1.13.0' 163) `
        -HeadVersion (New-TestVersion '1.13.0' 163) `
        -OriginVersion (New-TestVersion '1.13.0' 163) `
        -HeadSha ('e' * 40) `
        -OriginMainSha ('e' * 40) `
        -Conflicts $conflicts `
        -CommandRunner $runner

    Assert-ReleaseNull $selection.targetVersion
    Assert-ReleaseEqual $selection.baselineRelease.versionName '1.13.0'
    Assert-ReleaseEqual $selection.baselineRelease.versionCode 163
    Assert-ReleaseEqual (Get-NextStableVersion $selection.baselineRelease.versionName) '1.14.0'
}

Invoke-ReleaseTest 'Contract C：当前版本已为 1.14.0 时 Cleanup Pin 不得固定到旧版 1.13.0' {
    $stableReleases = @(
        [pscustomobject]@{
            tagName = 'v1.9.0'
            versionName = '1.9.0'
            versionCode = 157
            major = 1; minor = 9; patch = 0
            objectId = ('b' * 40)
            peeledSha = ('b' * 40)
        },
        [pscustomobject]@{
            tagName = 'v1.13.0'
            versionName = '1.13.0'
            versionCode = 163
            major = 1; minor = 13; patch = 0
            objectId = ('d' * 40)
            peeledSha = ('d' * 40)
        }
    )
    $remoteTags = @{
        'v1.9.0' = [pscustomobject]@{ objectId = ('b' * 40); peeledSha = ('b' * 40) }
        'v1.13.0' = [pscustomobject]@{ objectId = ('d' * 40); peeledSha = ('d' * 40) }
    }
    $conflicts = [System.Collections.Generic.List[string]]::new()
    $runner = {
        param([string] $FilePath, [string[]] $Arguments)
        return [pscustomobject]@{ exitCode = 0; output = '' }
    }

    $selection = Get-ReleaseTargetSelection `
        -StableReleases $stableReleases `
        -RemoteTags $remoteTags `
        -WorktreeVersion (New-TestVersion '1.14.0' 164) `
        -HeadVersion (New-TestVersion '1.14.0' 164) `
        -OriginVersion (New-TestVersion '1.14.0' 164) `
        -HeadSha ('e' * 40) `
        -OriginMainSha ('e' * 40) `
        -Conflicts $conflicts `
        -PinLatestStableTarget `
        -CommandRunner $runner

    Assert-ReleaseEqual $selection.targetVersion '1.14.0'
    Assert-ReleaseEqual $selection.baselineRelease.versionName '1.13.0'
    Assert-ReleaseEqual $selection.baselineRelease.versionCode 163
}

Invoke-ReleaseTest 'Contract D：Remove-ReleaseTemporaryFiles 只删除目标 Tag v1.13.0 的临时目录和 release-notes' {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-cleanup-test-$([guid]::NewGuid())"
    $artifactsRoot = Join-Path $root 'app/build/reports/release-artifacts'
    $assetsRoot = Join-Path $root 'app/build/reports/release-assets'
    $v13ArtifactDir = Join-Path $artifactsRoot 'v1.13.0-run42-attempt1'
    $v14ArtifactDir = Join-Path $artifactsRoot 'v1.14.0-run43-attempt1'
    $v13AssetDir = Join-Path $assetsRoot 'v1.13.0'
    $v14AssetDir = Join-Path $assetsRoot 'v1.14.0'
    $releaseNotesPath = Join-Path $root 'app/build/reports/release-notes.md'

    New-Item -ItemType Directory -Path $v13ArtifactDir -Force | Out-Null
    New-Item -ItemType Directory -Path $v14ArtifactDir -Force | Out-Null
    New-Item -ItemType Directory -Path $v13AssetDir -Force | Out-Null
    New-Item -ItemType Directory -Path $v14AssetDir -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $v13ArtifactDir '2026-07-16T12-34-56.apk'), 'v13-apk', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $v14ArtifactDir '2026-07-20T12-34-56.apk'), 'v14-apk', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $v13AssetDir 'info.json'), 'v13-info', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $v14AssetDir 'info.json'), 'v14-info', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($releaseNotesPath, '# 1memos Release Notes', [System.Text.UTF8Encoding]::new($false))

    try {
        Remove-ReleaseTemporaryFiles -ProjectDir $root -TargetTag 'v1.13.0'

        Assert-ReleaseEqual (Test-Path -LiteralPath $v13ArtifactDir -PathType Container) $false 'v1.13.0 artifact 目录应被删除'
        Assert-ReleaseEqual (Test-Path -LiteralPath $v13AssetDir -PathType Container) $false 'v1.13.0 assets 目录应被删除'
        Assert-ReleaseEqual (Test-Path -LiteralPath $releaseNotesPath -PathType Leaf) $false 'release-notes.md 应被删除'
        Assert-ReleaseEqual (Test-Path -LiteralPath $v14ArtifactDir -PathType Container) $true 'v1.14.0 artifact 目录应保留'
        Assert-ReleaseEqual (Test-Path -LiteralPath $v14AssetDir -PathType Container) $true 'v1.14.0 assets 目录应保留'
        Assert-ReleaseEqual (Test-Path -LiteralPath $artifactsRoot -PathType Container) $true 'release-artifacts 根目录应保留'
        Assert-ReleaseEqual (Test-Path -LiteralPath $assetsRoot -PathType Container) $true 'release-assets 根目录应保留'
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "RESULT passed=$script:Passed failed=$script:Failed"
if ($script:Failed -ne 0) { exit 1 }
