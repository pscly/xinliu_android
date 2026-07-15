package cc.pscly.onememos.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun resolveStableUpdate_acceptsHigherStableVersionWithOneVerifiedApk() {
        val release = stableRelease(tag = "v1.8.11")

        val result = resolveStableUpdate(release = release, currentVersionName = "1.8.10")

        requireNotNull(result)
        assertEquals("v1.8.11", result.tag)
        assertEquals("1.8.11", result.versionName)
        assertEquals(54_000_000L, result.apkSizeBytes)
        assertEquals("a".repeat(64), result.sha256)
    }

    @Test
    fun resolveStableUpdate_rejectsNonStableOrUnsafeReleases() {
        assertNull(resolveStableUpdate(stableRelease(tag = "benchmark-2026"), "1.8.10"))
        assertNull(resolveStableUpdate(stableRelease(tag = "v1.8.11", draft = true), "1.8.10"))
        assertNull(resolveStableUpdate(stableRelease(tag = "v1.8.11", prerelease = true), "1.8.10"))
        assertNull(resolveStableUpdate(stableRelease(tag = "v1.8.10"), "1.8.10"))
        assertNull(resolveStableUpdate(stableRelease(tag = "v1.7.99"), "1.8.10"))
        assertNull(
            resolveStableUpdate(
                stableRelease(tag = "v1.8.11", digest = null),
                "1.8.10",
            ),
        )
        assertNull(
            resolveStableUpdate(
                stableRelease(tag = "v1.8.11", downloadUrl = "http://github.com/release.apk"),
                "1.8.10",
            ),
        )
        assertNull(
            resolveStableUpdate(
                stableRelease(tag = "v1.8.11", extraApk = true),
                "1.8.10",
            ),
        )
    }

    @Test
    fun semanticVersion_comparesNumericComponents() {
        assertTrue(requireNotNull(SemanticVersion.parse("1.10.0")) > requireNotNull(SemanticVersion.parse("1.9.99")))
        assertEquals(SemanticVersion(1, 8, 11), SemanticVersion.parse("v1.8.11"))
        assertNull(SemanticVersion.parse("v1.8.11-beta"))
        assertNull(SemanticVersion.parse("1.8"))
    }

    @Test
    fun automaticPrompt_ignoredTagStaysHiddenUntilHigherVersionArrives() {
        assertFalse(
            shouldShowAutomaticUpdatePrompt(
                availableTag = "v1.8.11",
                ignoredTag = "v1.8.11",
                remindAfterEpochMs = 0L,
                nowEpochMs = 100L,
            ),
        )
        assertTrue(
            shouldShowAutomaticUpdatePrompt(
                availableTag = "v1.8.12",
                ignoredTag = "v1.8.11",
                remindAfterEpochMs = 0L,
                nowEpochMs = 100L,
            ),
        )
    }

    @Test
    fun automaticPrompt_laterSuppressesOnlyUntilReminderTime() {
        assertFalse(
            shouldShowAutomaticUpdatePrompt(
                availableTag = "v1.8.11",
                ignoredTag = "",
                remindAfterEpochMs = 200L,
                nowEpochMs = 199L,
            ),
        )
        assertTrue(
            shouldShowAutomaticUpdatePrompt(
                availableTag = "v1.8.11",
                ignoredTag = "",
                remindAfterEpochMs = 200L,
                nowEpochMs = 200L,
            ),
        )
    }

    @Test
    fun automaticCheck_successUses24HourCooldownAndFailureRetriesAfter1Hour() {
        val now = 1_000L

        assertEquals(now + 24 * 60 * 60 * 1_000L, nextAutomaticCheckAt(now, successful = true))
        assertEquals(now + 60 * 60 * 1_000L, nextAutomaticCheckAt(now, successful = false))
        assertFalse(shouldRunUpdateCheck(manual = false, nextAutomaticCheckAtEpochMs = 2_000L, nowEpochMs = 1_999L))
        assertTrue(shouldRunUpdateCheck(manual = false, nextAutomaticCheckAtEpochMs = 2_000L, nowEpochMs = 2_000L))
    }

    @Test
    fun manualCheck_bypassesAutomaticCooldown() {
        assertTrue(
            shouldRunUpdateCheck(
                manual = true,
                nextAutomaticCheckAtEpochMs = Long.MAX_VALUE,
                nowEpochMs = 0L,
            ),
        )
    }

    @Test
    fun downloadedApkMetadata_requiresExpectedPackageHigherVersionAndSameSigner() {
        val valid =
            DownloadedApkMetadata(
                packageName = "cc.pscly.onememos",
                versionName = "1.8.11",
                versionCode = 156L,
                signerSha256 = setOf("signer-a"),
            )

        assertNull(
            validateDownloadedApkMetadata(
                metadata = valid,
                expectedPackageName = "cc.pscly.onememos",
                expectedVersionName = "1.8.11",
                currentVersionCode = 155L,
                currentSignerSha256 = setOf("signer-a"),
            ),
        )
        assertEquals(
            ApkValidationFailure.PACKAGE_NAME,
            validateDownloadedApkMetadata(
                metadata = valid.copy(packageName = "invalid.package"),
                expectedPackageName = "cc.pscly.onememos",
                expectedVersionName = "1.8.11",
                currentVersionCode = 155L,
                currentSignerSha256 = setOf("signer-a"),
            ),
        )
        assertEquals(
            ApkValidationFailure.VERSION,
            validateDownloadedApkMetadata(
                metadata = valid.copy(versionCode = 155L),
                expectedPackageName = "cc.pscly.onememos",
                expectedVersionName = "1.8.11",
                currentVersionCode = 155L,
                currentSignerSha256 = setOf("signer-a"),
            ),
        )
        assertEquals(
            ApkValidationFailure.SIGNATURE,
            validateDownloadedApkMetadata(
                metadata = valid.copy(signerSha256 = setOf("signer-b")),
                expectedPackageName = "cc.pscly.onememos",
                expectedVersionName = "1.8.11",
                currentVersionCode = 155L,
                currentSignerSha256 = setOf("signer-a"),
            ),
        )
    }

    private fun stableRelease(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        digest: String? = "sha256:${"a".repeat(64)}",
        downloadUrl: String = "https://github.com/pscly/xinliu_android/releases/download/$tag/app.apk",
        extraApk: Boolean = false,
    ): GitHubReleaseDto {
        val apk =
            GitHubReleaseAssetDto(
                name = "app.apk",
                contentType = "application/vnd.android.package-archive",
                size = 54_000_000L,
                digest = digest,
                browserDownloadUrl = downloadUrl,
            )
        return GitHubReleaseDto(
            tagName = tag,
            name = "1memos $tag",
            body = "更新内容",
            publishedAt = "2026-07-13T00:00:00Z",
            draft = draft,
            prerelease = prerelease,
            assets = if (extraApk) listOf(apk, apk.copy(name = "other.apk")) else listOf(apk),
        )
    }
}
