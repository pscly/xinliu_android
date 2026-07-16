Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ReleaseRequiredRecoverySteps = @(
    'verifyArchitecture',
    'testDebugUnitTest',
    'lint',
    'assembleAppBenchmark',
    'assembleBaselineProfileBenchmark',
    'assembleMacrobenchmarkBenchmark',
    'verifyApk',
    'deviceReleaseExtensions'
)

function Get-ReleaseProperty {
    param(
        [AllowNull()] [object] $InputObject,
        [Parameter(Mandatory)] [string] $Name,
        [AllowNull()] [object] $DefaultValue = $null
    )

    if ($null -eq $InputObject) {
        return $DefaultValue
    }
    if ($InputObject -is [System.Collections.IDictionary]) {
        if ($InputObject.Contains($Name)) {
            return $InputObject[$Name]
        }
        return $DefaultValue
    }
    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $DefaultValue
    }
    return $property.Value
}

function Test-FullGitSha {
    param([AllowNull()] [object] $Value)

    return $Value -is [string] -and $Value -cmatch '^[0-9a-f]{40}$'
}

function ConvertTo-ReleaseVersion {
    param([Parameter(Mandatory)] [string] $Value)

    $match = [regex]::Match($Value.Trim(), '^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$')
    if (-not $match.Success) {
        throw "稳定版本格式无效：$Value"
    }
    return [pscustomobject]@{
        major = [int] $match.Groups[1].Value
        minor = [int] $match.Groups[2].Value
        patch = [int] $match.Groups[3].Value
        text = '{0}.{1}.{2}' -f $match.Groups[1].Value, $match.Groups[2].Value, $match.Groups[3].Value
    }
}

function Compare-ReleaseVersion {
    param(
        [Parameter(Mandatory)] [string] $Left,
        [Parameter(Mandatory)] [string] $Right
    )

    $leftVersion = ConvertTo-ReleaseVersion -Value $Left
    $rightVersion = ConvertTo-ReleaseVersion -Value $Right
    foreach ($field in @('major', 'minor', 'patch')) {
        if ($leftVersion.$field -lt $rightVersion.$field) { return -1 }
        if ($leftVersion.$field -gt $rightVersion.$field) { return 1 }
    }
    return 0
}

function Get-NextStableVersion {
    param([Parameter(Mandatory)] [string] $BaselineVersion)

    $version = ConvertTo-ReleaseVersion -Value $BaselineVersion
    return '{0}.{1}.0' -f $version.major, ($version.minor + 1)
}

function ConvertTo-CanonicalReleaseValue {
    param([AllowNull()] [object] $Value)

    if ($null -eq $Value) { return $null }
    if ($Value -is [DateTimeOffset]) {
        return ([DateTimeOffset] $Value).ToUniversalTime().ToString('o', [System.Globalization.CultureInfo]::InvariantCulture)
    }
    if ($Value -is [DateTime]) {
        return ([DateTimeOffset] ([DateTime] $Value)).ToUniversalTime().ToString('o', [System.Globalization.CultureInfo]::InvariantCulture)
    }
    if ($Value -is [string]) {
        if ($Value -cmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?(?:Z|[+-]\d{2}:\d{2})$') {
            $timestamp = [DateTimeOffset]::MinValue
            if ([DateTimeOffset]::TryParse(
                $Value,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::AllowWhiteSpaces,
                [ref] $timestamp
            )) {
                return $timestamp.ToUniversalTime().ToString('o', [System.Globalization.CultureInfo]::InvariantCulture)
            }
        }
        return $Value
    }
    if ($Value -is [sbyte] -or $Value -is [byte] -or
        $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64]) {
        return [int64] $Value
    }
    if ($Value -is [uint64]) {
        return [decimal] $Value
    }
    if ($Value -is [bool] -or $Value -is [ValueType]) {
        return $Value
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | ForEach-Object { [string] $_ } | Sort-Object -CaseSensitive)) {
            $ordered[$key] = ConvertTo-CanonicalReleaseValue -Value $Value[$key]
        }
        return $ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        return @($Value | ForEach-Object { ConvertTo-CanonicalReleaseValue -Value $_ })
    }
    $properties = [ordered]@{}
    foreach ($property in @($Value.PSObject.Properties | Sort-Object -Property Name -CaseSensitive)) {
        $properties[$property.Name] = ConvertTo-CanonicalReleaseValue -Value $property.Value
    }
    return $properties
}

function Get-ReleaseStringSha256 {
    param([Parameter(Mandatory)] [string] $Value)

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Get-RecoveryPayloadSha256 {
    param([Parameter(Mandatory)] [object] $Evidence)

    $copy = [ordered]@{}
    foreach ($property in @($Evidence.PSObject.Properties | Sort-Object -Property Name -CaseSensitive)) {
        if ($property.Name -ne 'payloadSha256') {
            $copy[$property.Name] = ConvertTo-CanonicalReleaseValue -Value $property.Value
        }
    }
    $json = $copy | ConvertTo-Json -Depth 100 -Compress
    return Get-ReleaseStringSha256 -Value $json
}

function Test-RecoveryEvidence {
    param(
        [AllowNull()] [object] $Evidence,
        [Parameter(Mandatory)] [object] $Expected
    )

    if ($null -eq $Evidence) {
        return [pscustomobject]@{ status = 'Missing'; reasonCode = 'RECOVERY_EVIDENCE_MISSING' }
    }

    $checks = @(
        @('kind', 'same-version-tag-recovery-local-gate'),
        @('targetTag', (Get-ReleaseProperty $Expected 'targetTag')),
        @('targetVersion', (Get-ReleaseProperty $Expected 'targetVersion')),
        @('targetVersionCode', (Get-ReleaseProperty $Expected 'targetVersionCode')),
        @('candidateSha', (Get-ReleaseProperty $Expected 'candidateSha')),
        @('oldTagObjectId', (Get-ReleaseProperty $Expected 'oldTagObjectId')),
        @('oldTagPeeledSha', (Get-ReleaseProperty $Expected 'oldTagPeeledSha')),
        @('prePushOriginMainSha', (Get-ReleaseProperty $Expected 'prePushOriginMainSha'))
    )
    foreach ($check in $checks) {
        if ((Get-ReleaseProperty $Evidence $check[0]) -cne $check[1]) {
            return [pscustomobject]@{ status = 'Stale'; reasonCode = "RECOVERY_BINDING_$($check[0].ToUpperInvariant())" }
        }
    }

    foreach ($shaField in @('candidateSha', 'oldTagObjectId', 'oldTagPeeledSha', 'prePushOriginMainSha')) {
        if (-not (Test-FullGitSha (Get-ReleaseProperty $Evidence $shaField))) {
            return [pscustomobject]@{ status = 'Stale'; reasonCode = "RECOVERY_INVALID_$($shaField.ToUpperInvariant())" }
        }
    }

    if ([int] (Get-ReleaseProperty $Evidence 'schemaVersion' 0) -ne 1) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_SCHEMA_VERSION' }
    }
    $repository = Get-ReleaseProperty $Evidence 'repository'
    $repositoryChecks = @(
        @('branchBefore', 'main'),
        @('headBefore', (Get-ReleaseProperty $Expected 'candidateSha')),
        @('worktreeCleanBefore', $true),
        @('indexCleanBefore', $true),
        @('originMainBefore', (Get-ReleaseProperty $Expected 'prePushOriginMainSha')),
        @('tagObjectBefore', (Get-ReleaseProperty $Expected 'oldTagObjectId')),
        @('tagPeeledBefore', (Get-ReleaseProperty $Expected 'oldTagPeeledSha')),
        @('branchAfter', 'main'),
        @('headAfter', (Get-ReleaseProperty $Expected 'candidateSha')),
        @('worktreeCleanAfter', $true),
        @('indexCleanAfter', $true),
        @('originMainAfter', (Get-ReleaseProperty $Expected 'prePushOriginMainSha')),
        @('tagObjectAfter', (Get-ReleaseProperty $Expected 'oldTagObjectId')),
        @('tagPeeledAfter', (Get-ReleaseProperty $Expected 'oldTagPeeledSha'))
    )
    foreach ($check in $repositoryChecks) {
        if ((Get-ReleaseProperty $repository $check[0]) -cne $check[1]) {
            return [pscustomobject]@{ status = 'Stale'; reasonCode = "RECOVERY_REPOSITORY_$($check[0].ToUpperInvariant())" }
        }
    }

    $failedRun = Get-ReleaseProperty $Evidence 'failedRun'
    if ((Get-ReleaseProperty $failedRun 'workflow') -cne 'android-benchmark.yml' -or
        (Get-ReleaseProperty $failedRun 'event') -cne 'push' -or
        (Get-ReleaseProperty $failedRun 'headBranch') -cne (Get-ReleaseProperty $Expected 'targetTag') -or
        (Get-ReleaseProperty $failedRun 'headSha') -cne (Get-ReleaseProperty $Expected 'oldTagPeeledSha') -or
        (Get-ReleaseProperty $failedRun 'status') -cne 'completed' -or
        (Get-ReleaseProperty $failedRun 'conclusion') -in @($null, 'success') -or
        [int64] (Get-ReleaseProperty $failedRun 'databaseId' 0) -le 0 -or
        [int] (Get-ReleaseProperty $failedRun 'attempt' 0) -le 0 -or
        [string]::IsNullOrWhiteSpace([string] (Get-ReleaseProperty $failedRun 'completedAt'))) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_FAILED_RUN' }
    }

    $gate = Get-ReleaseProperty $Evidence 'gate'
    if ((Get-ReleaseProperty $gate 'profile') -cne 'full-release' -or
        [string]::IsNullOrWhiteSpace([string] (Get-ReleaseProperty $gate 'startedAt')) -or
        [string]::IsNullOrWhiteSpace([string] (Get-ReleaseProperty $gate 'completedAt'))) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_GATE_PROFILE' }
    }
    $steps = @(Get-ReleaseProperty $gate 'steps' @())
    $stepIds = @($steps | ForEach-Object { [string] (Get-ReleaseProperty $_ 'id') })
    if ($stepIds.Count -ne $script:ReleaseRequiredRecoverySteps.Count -or
        (Compare-Object ($stepIds | Sort-Object) ($script:ReleaseRequiredRecoverySteps | Sort-Object))) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_GATE_STEPS' }
    }
    foreach ($step in $steps) {
        if ([int] (Get-ReleaseProperty $step 'exitCode' -1) -ne 0) {
            return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_GATE_FAILED' }
        }
    }

    $artifact = Get-ReleaseProperty $Evidence 'artifact'
    $walkthrough = Get-ReleaseProperty $Evidence 'walkthrough'
    if ((Get-ReleaseProperty $artifact 'packageName') -cne 'cc.pscly.onememos' -or
        (Get-ReleaseProperty $artifact 'fileName') -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.apk$' -or
        (Get-ReleaseProperty $artifact 'versionName') -cne (Get-ReleaseProperty $Expected 'targetVersion') -or
        [int] (Get-ReleaseProperty $artifact 'versionCode' -1) -ne [int] (Get-ReleaseProperty $Expected 'targetVersionCode') -or
        (Get-ReleaseProperty $artifact 'sha256') -cnotmatch '^[0-9a-f]{64}$' -or
        (Get-ReleaseProperty $artifact 'signerSha256') -cne (Get-ReleaseProperty $Expected 'signerSha256') -or
        (Get-ReleaseProperty $walkthrough 'checklistId') -cne 'task35-release-walkthrough-v1' -or
        (Get-ReleaseProperty $walkthrough 'completed' $false) -ne $true -or
        (Get-ReleaseProperty $walkthrough 'candidateSha') -cne (Get-ReleaseProperty $Expected 'candidateSha') -or
        (Get-ReleaseProperty $walkthrough 'apkSha256') -cne (Get-ReleaseProperty $artifact 'sha256') -or
        [string]::IsNullOrWhiteSpace([string] (Get-ReleaseProperty $walkthrough 'confirmedAt')) -or
        [string]::IsNullOrWhiteSpace([string] (Get-ReleaseProperty $Evidence 'createdAt'))) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_ARTIFACT_OR_WALKTHROUGH' }
    }

    $payloadSha = Get-ReleaseProperty $Evidence 'payloadSha256'
    if ($payloadSha -notmatch '^[0-9a-f]{64}$' -or $payloadSha -cne (Get-RecoveryPayloadSha256 -Evidence $Evidence)) {
        return [pscustomobject]@{ status = 'Stale'; reasonCode = 'RECOVERY_PAYLOAD_SHA256' }
    }
    return [pscustomobject]@{ status = 'Valid'; reasonCode = 'RECOVERY_EVIDENCE_VALID' }
}

function New-ReleaseStateResult {
    param(
        [Parameter(Mandatory)] [string] $State,
        [Parameter(Mandatory)] [string] $ReasonCode,
        [Parameter(Mandatory)] [string] $TargetVersion,
        [Parameter(Mandatory)] [int] $TargetVersionCode,
        [Parameter(Mandatory)] [string] $TargetTag,
        [AllowNull()] [object] $TargetSha,
        [Parameter(Mandatory)] [object] $Facts
    )

    $baseline = Get-ReleaseProperty $Facts 'baseline'
    $remoteTag = Get-ReleaseProperty $Facts 'remoteTag' ([pscustomobject]@{})
    $tagRun = Get-ReleaseProperty $Facts 'tagRun' ([pscustomobject]@{})
    $artifact = Get-ReleaseProperty $Facts 'artifact' ([pscustomobject]@{})
    $release = Get-ReleaseProperty $Facts 'release' ([pscustomobject]@{})
    $recovery = Get-ReleaseProperty $Facts 'recovery' ([pscustomobject]@{})
    return [pscustomobject]@{
        state = $State
        reasonCode = $ReasonCode
        targetVersion = $TargetVersion
        targetVersionCode = $TargetVersionCode
        targetTag = $TargetTag
        targetSha = $TargetSha
        baselineTag = [string] (Get-ReleaseProperty $baseline 'tag')
        cacheStatus = [string] (Get-ReleaseProperty $Facts 'cacheStatus' 'Missing')
        tag = [pscustomobject]@{
            exists = [bool] (Get-ReleaseProperty $remoteTag 'exists' $false)
            objectId = Get-ReleaseProperty $remoteTag 'objectId'
            peeledSha = Get-ReleaseProperty $remoteTag 'peeledSha'
        }
        run = $tagRun
        artifact = $artifact
        release = $release
        recovery = [pscustomobject]@{
            oldTagObjectId = Get-ReleaseProperty $recovery 'oldTagObjectId'
            oldTagPeeledSha = Get-ReleaseProperty $recovery 'oldTagPeeledSha'
            candidateSha = Get-ReleaseProperty $recovery 'candidateSha'
            prePushOriginMainSha = Get-ReleaseProperty $recovery 'prePushOriginMainSha'
            gateEvidencePath = Get-ReleaseProperty $recovery 'gateEvidencePath'
            gateEvidenceSha256 = Get-ReleaseProperty $recovery 'gateEvidenceSha256'
            gateEvidenceStatus = [string] (Get-ReleaseProperty $recovery 'gateEvidenceStatus' 'Missing')
            authorizationRequired = [bool] (Get-ReleaseProperty $recovery 'authorizationRequired' $false)
            invalidatedSha = Get-ReleaseProperty $recovery 'invalidatedSha'
        }
        transition = Get-ReleaseTransition -State $State
    }
}

function Get-ReleaseTransition {
    param([Parameter(Mandatory)] [string] $State)

    $transitions = @{
        NewRelease = 'Prepare'
        PreparedWorktree = 'Prepare'
        Prepared = 'PushMain'
        MainPushed = 'PushTag'
        AwaitingTagActions = 'WaitTagActions'
        TagActionsFailed = 'StopOrRerunSameTag'
        RecoveryCandidate = 'PrepareRecovery'
        RecoveryPrepared = 'PushRecoveryMain'
        TagRecoveryRequired = 'RequestAuthorizationThenRecoverTag'
        TagArtifactUnavailable = 'RerunSameTag'
        TagActionsSucceeded = 'PublishRelease'
        ReleaseIncomplete = 'PublishRelease'
        ReleasePublished = 'VerifyRelease'
        Completed = 'Cleanup'
        Conflict = 'Stop'
    }
    if (-not $transitions.ContainsKey($State)) {
        throw "未知发布状态：$State"
    }
    return $transitions[$State]
}

function Resolve-ReleaseState {
    param([Parameter(Mandatory)] [object] $Facts)

    $baseline = Get-ReleaseProperty $Facts 'baseline'
    $repository = Get-ReleaseProperty $Facts 'repository'
    $versions = Get-ReleaseProperty $Facts 'versions'
    $remoteTag = Get-ReleaseProperty $Facts 'remoteTag' ([pscustomobject]@{})
    $tagRun = Get-ReleaseProperty $Facts 'tagRun' ([pscustomobject]@{})
    $artifact = Get-ReleaseProperty $Facts 'artifact' ([pscustomobject]@{})
    $release = Get-ReleaseProperty $Facts 'release' ([pscustomobject]@{})
    $recovery = Get-ReleaseProperty $Facts 'recovery' ([pscustomobject]@{})

    $baselineVersion = [string] (Get-ReleaseProperty $baseline 'versionName')
    $baselineCode = [int] (Get-ReleaseProperty $baseline 'versionCode')
    $baselineTag = [string] (Get-ReleaseProperty $baseline 'tag')
    $worktreeVersion = Get-ReleaseProperty (Get-ReleaseProperty $versions 'worktree') 'versionName'
    $worktreeCode = Get-ReleaseProperty (Get-ReleaseProperty $versions 'worktree') 'versionCode'
    $headVersion = Get-ReleaseProperty (Get-ReleaseProperty $versions 'head') 'versionName'
    $headCode = Get-ReleaseProperty (Get-ReleaseProperty $versions 'head') 'versionCode'
    $originVersion = Get-ReleaseProperty (Get-ReleaseProperty $versions 'originMain') 'versionName'
    $originCode = Get-ReleaseProperty (Get-ReleaseProperty $versions 'originMain') 'versionCode'

    $remoteExists = [bool] (Get-ReleaseProperty $remoteTag 'exists' $false)
    $targetVersion = $null
    $targetCode = $null
    if ($remoteExists) {
        $targetVersion = [string] (Get-ReleaseProperty $remoteTag 'versionName')
        $targetCode = [int] (Get-ReleaseProperty $remoteTag 'versionCode')
    } elseif ($null -ne $originVersion -and (Compare-ReleaseVersion $originVersion $baselineVersion) -gt 0) {
        $targetVersion = [string] $originVersion
        $targetCode = [int] $originCode
    } elseif ($null -ne $headVersion -and (Compare-ReleaseVersion $headVersion $baselineVersion) -gt 0) {
        $targetVersion = [string] $headVersion
        $targetCode = [int] $headCode
    } elseif ($null -ne $worktreeVersion -and (Compare-ReleaseVersion $worktreeVersion $baselineVersion) -gt 0) {
        $targetVersion = [string] $worktreeVersion
        $targetCode = [int] $worktreeCode
    } else {
        $targetVersion = Get-NextStableVersion -BaselineVersion $baselineVersion
        $maxCode = [Math]::Max($baselineCode, [int] $originCode)
        $targetCode = $maxCode + 1
    }
    $targetTag = "v$targetVersion"

    $targetSha = $null
    if ($remoteExists) {
        $targetSha = Get-ReleaseProperty $remoteTag 'peeledSha'
    } elseif ($headVersion -ceq $targetVersion -and (Test-FullGitSha (Get-ReleaseProperty $repository 'headSha'))) {
        $targetSha = [string] (Get-ReleaseProperty $repository 'headSha')
    } elseif ($originVersion -ceq $targetVersion -and (Test-FullGitSha (Get-ReleaseProperty $repository 'originMainSha'))) {
        $targetSha = [string] (Get-ReleaseProperty $repository 'originMainSha')
    }

    $conflicts = @(Get-ReleaseProperty $Facts 'conflicts' @())
    $candidateVersions = @($worktreeVersion, $headVersion, $originVersion | Where-Object {
        $null -ne $_ -and (Compare-ReleaseVersion ([string] $_) $baselineVersion) -gt 0
    } | Select-Object -Unique)
    $invalidCache = [string] (Get-ReleaseProperty $Facts 'cacheStatus' 'Missing') -notin @('Missing', 'Consistent', 'Stale')
    $toolchainValid = [bool] (Get-ReleaseProperty (Get-ReleaseProperty $Facts 'toolchain') 'valid' $true)
    if ($conflicts.Count -gt 0 -or $candidateVersions.Count -gt 1 -or $invalidCache -or -not $toolchainValid -or
        [string]::IsNullOrWhiteSpace($baselineVersion) -or $baselineTag -cne "v$baselineVersion" -or
        $targetCode -le $baselineCode -or [string]::IsNullOrWhiteSpace($targetVersion)) {
        return New-ReleaseStateResult 'Conflict' 'UNSAFE_OR_AMBIGUOUS_FACTS' $targetVersion $targetCode $targetTag $targetSha $Facts
    }

    if ($remoteExists) {
        $objectId = Get-ReleaseProperty $remoteTag 'objectId'
        $peeledSha = Get-ReleaseProperty $remoteTag 'peeledSha'
        if (-not (Test-FullGitSha $objectId) -or -not (Test-FullGitSha $peeledSha) -or
            (Get-ReleaseProperty $remoteTag 'tagName') -cne $targetTag) {
            return New-ReleaseStateResult 'Conflict' 'REMOTE_TAG_CONFLICT' $targetVersion $targetCode $targetTag $targetSha $Facts
        }

        $releaseExists = [bool] (Get-ReleaseProperty $release 'exists' $false)
        if ($releaseExists) {
            $releaseRunVerified =
                [bool] (Get-ReleaseProperty $tagRun 'found' $false) -and
                (Get-ReleaseProperty $tagRun 'workflow') -ceq 'android-benchmark.yml' -and
                (Get-ReleaseProperty $tagRun 'event') -ceq 'push' -and
                (Get-ReleaseProperty $tagRun 'headBranch') -ceq $targetTag -and
                (Get-ReleaseProperty $tagRun 'headSha') -ceq $targetSha -and
                (Get-ReleaseProperty $tagRun 'status') -ceq 'completed' -and
                (Get-ReleaseProperty $tagRun 'conclusion') -ceq 'success'
            $releaseArtifactVerified =
                [int] (Get-ReleaseProperty $artifact 'count' 0) -eq 1 -and
                -not [bool] (Get-ReleaseProperty $artifact 'expired' $false) -and
                (Get-ReleaseProperty $artifact 'nameMatches' $false) -eq $true -and
                (Get-ReleaseProperty $artifact 'versionMatches' $false) -eq $true -and
                (Get-ReleaseProperty $artifact 'verified' $false) -eq $true
            if (-not $releaseRunVerified -or -not $releaseArtifactVerified) {
                return New-ReleaseStateResult 'Conflict' 'RELEASE_WITHOUT_VERIFIED_TAG_ARTIFACT' $targetVersion $targetCode $targetTag $targetSha $Facts
            }
            $apkCount = [int] (Get-ReleaseProperty $release 'apkCount' 0)
            if ((Get-ReleaseProperty $release 'draft' $false) -or (Get-ReleaseProperty $release 'prerelease' $false) -or
                (Get-ReleaseProperty $release 'tagName') -cne $targetTag -or $apkCount -gt 1) {
                return New-ReleaseStateResult 'Conflict' 'RELEASE_CONFLICT' $targetVersion $targetCode $targetTag $targetSha $Facts
            }
            if ($apkCount -eq 0) {
                return New-ReleaseStateResult 'ReleaseIncomplete' 'RELEASE_APK_MISSING' $targetVersion $targetCode $targetTag $targetSha $Facts
            }
            if ([bool] (Get-ReleaseProperty $release 'deepVerified' $false)) {
                if ((Get-ReleaseProperty $release 'isLatest' $false) -ne $true -or
                    (Get-ReleaseProperty $release 'apkSha256') -cne (Get-ReleaseProperty $artifact 'sha256')) {
                    return New-ReleaseStateResult 'Conflict' 'RELEASE_DEEP_VERIFICATION_FAILED' $targetVersion $targetCode $targetTag $targetSha $Facts
                }
                return New-ReleaseStateResult 'Completed' 'RELEASE_DEEP_VERIFIED' $targetVersion $targetCode $targetTag $targetSha $Facts
            }
            return New-ReleaseStateResult 'ReleasePublished' 'RELEASE_REVERIFY_REQUIRED' $targetVersion $targetCode $targetTag $targetSha $Facts
        }

        $runFound = [bool] (Get-ReleaseProperty $tagRun 'found' $false)
        if (-not $runFound -or (Get-ReleaseProperty $tagRun 'status') -in @('queued', 'in_progress', 'waiting', 'pending')) {
            return New-ReleaseStateResult 'AwaitingTagActions' 'TAG_ACTIONS_PENDING' $targetVersion $targetCode $targetTag $targetSha $Facts
        }
        if ((Get-ReleaseProperty $tagRun 'workflow') -cne 'android-benchmark.yml' -or
            (Get-ReleaseProperty $tagRun 'event') -cne 'push' -or
            (Get-ReleaseProperty $tagRun 'headBranch') -cne $targetTag -or
            (Get-ReleaseProperty $tagRun 'headSha') -cne $targetSha) {
            return New-ReleaseStateResult 'Conflict' 'TAG_ACTIONS_IDENTITY_MISMATCH' $targetVersion $targetCode $targetTag $targetSha $Facts
        }

        if ((Get-ReleaseProperty $tagRun 'conclusion') -cne 'success') {
            $candidateSha = Get-ReleaseProperty $recovery 'candidateSha'
            $hasCandidate = Test-FullGitSha $candidateSha
            if ($hasCandidate) {
                $evidenceStatus = [string] (Get-ReleaseProperty $recovery 'gateEvidenceStatus' 'Missing')
                $originMainSha = [string] (Get-ReleaseProperty $repository 'originMainSha')
                if ($originMainSha -ceq $candidateSha) {
                    if ($evidenceStatus -ne 'Valid') {
                        return New-ReleaseStateResult 'Conflict' 'RECOVERY_PRE_PUSH_EVIDENCE_MISSING' $targetVersion $targetCode $targetTag $targetSha $Facts
                    }
                    return New-ReleaseStateResult 'TagRecoveryRequired' 'TAG_RECOVERY_AUTHORIZATION_REQUIRED' $targetVersion $targetCode $targetTag $targetSha $Facts
                }
                if ($evidenceStatus -eq 'Valid') {
                    return New-ReleaseStateResult 'RecoveryPrepared' 'RECOVERY_GATE_VALID' $targetVersion $targetCode $targetTag $targetSha $Facts
                }
                return New-ReleaseStateResult 'RecoveryCandidate' 'RECOVERY_GATE_REQUIRED' $targetVersion $targetCode $targetTag $targetSha $Facts
            }
            return New-ReleaseStateResult 'TagActionsFailed' 'TAG_ACTIONS_FAILED' $targetVersion $targetCode $targetTag $targetSha $Facts
        }

        if ([int] (Get-ReleaseProperty $artifact 'count' 0) -ne 1 -or
            [bool] (Get-ReleaseProperty $artifact 'expired' $false) -or
            (Get-ReleaseProperty $artifact 'nameMatches' $false) -ne $true -or
            (Get-ReleaseProperty $artifact 'versionMatches' $false) -ne $true -or
            (Get-ReleaseProperty $artifact 'verified' $false) -ne $true) {
            return New-ReleaseStateResult 'TagArtifactUnavailable' 'TAG_ARTIFACT_UNAVAILABLE' $targetVersion $targetCode $targetTag $targetSha $Facts
        }
        return New-ReleaseStateResult 'TagActionsSucceeded' 'TAG_ACTIONS_AND_ARTIFACT_VERIFIED' $targetVersion $targetCode $targetTag $targetSha $Facts
    }

    $clean = [bool] (Get-ReleaseProperty $repository 'clean' $false)
    $headSha = [string] (Get-ReleaseProperty $repository 'headSha')
    $originMainSha = [string] (Get-ReleaseProperty $repository 'originMainSha')
    if ($headVersion -ceq $targetVersion -and $originVersion -ceq $targetVersion -and $headSha -ceq $originMainSha -and $clean) {
        return New-ReleaseStateResult 'MainPushed' 'MAIN_PUSHED_TAG_MISSING' $targetVersion $targetCode $targetTag $headSha $Facts
    }
    if ($headVersion -ceq $targetVersion -and $clean -and (Get-ReleaseProperty $repository 'headIsOriginMainDescendant' $false)) {
        return New-ReleaseStateResult 'Prepared' 'RELEASE_COMMIT_READY' $targetVersion $targetCode $targetTag $headSha $Facts
    }
    $allowedDirty = @('app/build.gradle.kts', '.ai_session.md')
    $dirtyPaths = @(Get-ReleaseProperty $repository 'dirtyPaths' @())
    $unexpectedDirty = @($dirtyPaths | Where-Object { $_ -notin $allowedDirty })
    if ($worktreeVersion -ceq $targetVersion -and $headVersion -ceq $baselineVersion -and $unexpectedDirty.Count -eq 0) {
        return New-ReleaseStateResult 'PreparedWorktree' 'VERSION_PREPARED_GATE_REQUIRED' $targetVersion $targetCode $targetTag $null $Facts
    }
    if ($worktreeVersion -ceq $baselineVersion -and $headVersion -ceq $baselineVersion -and $originVersion -ceq $baselineVersion) {
        return New-ReleaseStateResult 'NewRelease' 'NEXT_STABLE_VERSION_REQUIRED' $targetVersion $targetCode $targetTag $null $Facts
    }
    return New-ReleaseStateResult 'Conflict' 'UNCLASSIFIED_RELEASE_FACTS' $targetVersion $targetCode $targetTag $targetSha $Facts
}
