package cc.pscly.onememos.update

import com.google.gson.annotations.SerializedName
import java.net.URI

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        private val pattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

        fun parse(raw: String): SemanticVersion? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            return runCatching {
                SemanticVersion(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toInt(),
                    patch = match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
    }
}

data class GitHubReleaseAssetDto(
    val name: String,
    @SerializedName("content_type") val contentType: String,
    val size: Long,
    val digest: String?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAssetDto>,
)

data class AppUpdateRelease(
    val tag: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String,
)

fun resolveStableUpdate(
    release: GitHubReleaseDto,
    currentVersionName: String,
): AppUpdateRelease? {
    if (release.draft || release.prerelease || !release.tagName.startsWith("v")) return null

    val remoteVersion = SemanticVersion.parse(release.tagName) ?: return null
    val currentVersion = SemanticVersion.parse(currentVersionName) ?: return null
    if (remoteVersion <= currentVersion) return null

    val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apkAssets.size != 1) return null
    val apk = apkAssets.single()
    if (apk.contentType != APK_CONTENT_TYPE || apk.size <= 0L) return null
    if (!apk.browserDownloadUrl.isTrustedGitHubDownloadUrl()) return null

    val sha256 = apk.digest?.removePrefix("sha256:")?.lowercase() ?: return null
    if (!SHA256_PATTERN.matches(sha256)) return null

    return AppUpdateRelease(
        tag = release.tagName,
        versionName = release.tagName.removePrefix("v"),
        title = release.name.orEmpty().ifBlank { release.tagName },
        notes = release.body.orEmpty(),
        publishedAt = release.publishedAt.orEmpty(),
        apkName = apk.name,
        apkUrl = apk.browserDownloadUrl,
        apkSizeBytes = apk.size,
        sha256 = sha256,
    )
}

fun shouldShowAutomaticUpdatePrompt(
    availableTag: String,
    ignoredTag: String,
    remindAfterEpochMs: Long,
    nowEpochMs: Long,
): Boolean {
    val available = SemanticVersion.parse(availableTag) ?: return false
    val ignored = SemanticVersion.parse(ignoredTag)
    if (ignored != null && available <= ignored) return false
    return nowEpochMs >= remindAfterEpochMs
}

fun nextAutomaticCheckAt(
    nowEpochMs: Long,
    successful: Boolean,
): Long = nowEpochMs + if (successful) SUCCESS_CHECK_INTERVAL_MS else FAILED_CHECK_RETRY_MS

fun shouldRunUpdateCheck(
    manual: Boolean,
    nextAutomaticCheckAtEpochMs: Long,
    nowEpochMs: Long,
): Boolean = manual || nowEpochMs >= nextAutomaticCheckAtEpochMs

data class DownloadedApkMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

enum class ApkValidationFailure {
    PACKAGE_NAME,
    VERSION,
    SIGNATURE,
}

fun validateDownloadedApkMetadata(
    metadata: DownloadedApkMetadata,
    expectedPackageName: String,
    expectedVersionName: String,
    currentVersionCode: Long,
    currentSignerSha256: Set<String>,
): ApkValidationFailure? =
    when {
        metadata.packageName != expectedPackageName -> ApkValidationFailure.PACKAGE_NAME
        metadata.versionName != expectedVersionName || metadata.versionCode <= currentVersionCode ->
            ApkValidationFailure.VERSION
        metadata.signerSha256.isEmpty() || metadata.signerSha256 != currentSignerSha256 ->
            ApkValidationFailure.SIGNATURE
        else -> null
    }

private fun String.isTrustedGitHubDownloadUrl(): Boolean =
    runCatching {
        val uri = URI(this)
        uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("github.com", ignoreCase = true)
    }.getOrDefault(false)

private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
private const val SUCCESS_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1_000L
private const val FAILED_CHECK_RETRY_MS = 60 * 60 * 1_000L
private val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
