package cc.pscly.onememos.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import cc.pscly.onememos.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AppUpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    UP_TO_DATE,
    ERROR,
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val release: AppUpdateRelease? = null,
    val ignoredVersionTag: String = "",
    val promptVisible: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val statusMessage: String = "尚未检查更新",
    val downloadedApkPath: String = "",
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GitHubUpdateApi,
    private val store: AppUpdateStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private var downloadMonitorJob: Job? = null
    private var installRequestedAfterPermission = false

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val preferences = store.snapshot()
            _uiState.update { it.copy(ignoredVersionTag = preferences.ignoredVersionTag) }
            val release = preferences.downloadRelease
            if (preferences.downloadId > 0L && release != null && preferences.downloadFilePath.isNotBlank()) {
                monitorDownload(
                    downloadId = preferences.downloadId,
                    apkFile = File(preferences.downloadFilePath),
                    release = release,
                )
            }
        }
    }

    fun checkForUpdates(manual: Boolean) {
        scope.launch {
            checkMutex.withLock {
                if (
                    _uiState.value.phase == AppUpdatePhase.CHECKING ||
                    _uiState.value.phase == AppUpdatePhase.DOWNLOADING ||
                    _uiState.value.phase == AppUpdatePhase.READY_TO_INSTALL
                ) {
                    return@withLock
                }
                val now = System.currentTimeMillis()
                val preferences = store.snapshot()
                // 启动恢复协程会接管持久化下载；检查请求不能覆盖下载/待安装状态。
                if (preferences.downloadId > 0L && preferences.downloadRelease != null) return@withLock
                if (!shouldRunUpdateCheck(manual, preferences.nextAutomaticCheckAtEpochMs, now)) return@withLock

                _uiState.update {
                    it.copy(
                        phase = AppUpdatePhase.CHECKING,
                        promptVisible = false,
                        statusMessage = "正在检查更新...",
                    )
                }

                runCatching { api.latestStableRelease() }
                    .onSuccess { dto ->
                        store.setNextAutomaticCheckAt(nextAutomaticCheckAt(now, successful = true))
                        val release = resolveStableUpdate(dto, BuildConfig.VERSION_NAME)
                        if (release == null) {
                            _uiState.update {
                                it.copy(
                                    phase = if (manual) AppUpdatePhase.UP_TO_DATE else AppUpdatePhase.IDLE,
                                    release = null,
                                    promptVisible = false,
                                    statusMessage = "当前已是最新稳定版",
                                    ignoredVersionTag = preferences.ignoredVersionTag,
                                )
                            }
                        } else {
                            val showPrompt =
                                !manual &&
                                    shouldShowAutomaticUpdatePrompt(
                                        availableTag = release.tag,
                                        ignoredTag = preferences.ignoredVersionTag,
                                        remindAfterEpochMs = preferences.remindAfterEpochMs,
                                        nowEpochMs = now,
                                    )
                            _uiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.AVAILABLE,
                                    release = release,
                                    promptVisible = showPrompt,
                                    statusMessage =
                                        if (release.tag == preferences.ignoredVersionTag) {
                                            "发现 ${release.versionName}，此版本已忽略"
                                        } else {
                                            "发现新版本 ${release.versionName}"
                                        },
                                    ignoredVersionTag = preferences.ignoredVersionTag,
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        store.setNextAutomaticCheckAt(nextAutomaticCheckAt(now, successful = false))
                        _uiState.update {
                            it.copy(
                                phase = if (manual) AppUpdatePhase.ERROR else AppUpdatePhase.IDLE,
                                promptVisible = false,
                                statusMessage = "检查更新失败：${error.readableMessage()}",
                                ignoredVersionTag = preferences.ignoredVersionTag,
                            )
                        }
                    }
            }
        }
    }

    fun remindLater() {
        val release = _uiState.value.release ?: return
        scope.launch {
            store.setRemindAfter(System.currentTimeMillis() + REMIND_LATER_MS)
            _uiState.update {
                it.copy(
                    promptVisible = false,
                    statusMessage = "已稍后提醒 ${release.versionName}",
                )
            }
        }
    }

    fun ignoreCurrentVersion() {
        val release = _uiState.value.release ?: return
        scope.launch {
            store.setIgnoredVersionTag(release.tag)
            _uiState.update {
                it.copy(
                    ignoredVersionTag = release.tag,
                    promptVisible = false,
                    statusMessage = "已忽略版本 ${release.versionName}",
                )
            }
        }
    }

    fun clearIgnoredVersion() {
        scope.launch {
            store.setIgnoredVersionTag("")
            store.setRemindAfter(0L)
            _uiState.update {
                val release = it.release
                it.copy(
                    ignoredVersionTag = "",
                    statusMessage = release?.let { item -> "已取消忽略 ${item.versionName}" } ?: "已取消忽略版本",
                )
            }
        }
    }

    fun dismissPrompt() {
        _uiState.update { it.copy(promptVisible = false) }
    }

    fun startDownload() {
        val release = _uiState.value.release ?: return
        if (_uiState.value.phase == AppUpdatePhase.DOWNLOADING) return
        scope.launch {
            runCatching {
                val downloadsRoot =
                    requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
                        "应用下载目录不可用"
                    }
                val updateDir = downloadsRoot.resolve("updates").apply { mkdirs() }
                val safeName = release.apkName.substringAfterLast('/').replace(UNSAFE_FILE_CHARS, "_")
                val apkFile = updateDir.resolve("${release.tag}-$safeName")
                if (apkFile.exists() && !apkFile.delete()) error("无法清理旧安装包")

                val request =
                    DownloadManager.Request(Uri.parse(release.apkUrl))
                        .setTitle("1memos ${release.versionName}")
                        .setDescription("正在下载应用更新")
                        .setMimeType(APK_CONTENT_TYPE)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationInExternalFilesDir(
                            context,
                            Environment.DIRECTORY_DOWNLOADS,
                            "updates/${apkFile.name}",
                        )
                val downloadId = downloadManager.enqueue(request)
                store.saveDownload(downloadId, apkFile.absolutePath, release)
                _uiState.update {
                    it.copy(
                        phase = AppUpdatePhase.DOWNLOADING,
                        promptVisible = true,
                        downloadProgressPercent = 0,
                        statusMessage = "正在下载 ${release.versionName}",
                        downloadedApkPath = apkFile.absolutePath,
                    )
                }
                monitorDownload(downloadId, apkFile, release)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        phase = AppUpdatePhase.ERROR,
                        promptVisible = false,
                        statusMessage = "无法开始下载：${error.readableMessage()}",
                    )
                }
            }
        }
    }

    fun requestInstall() {
        val state = _uiState.value
        if (state.phase != AppUpdatePhase.READY_TO_INSTALL || state.downloadedApkPath.isBlank()) return
        installRequestedAfterPermission = true
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _uiState.update { it.copy(statusMessage = "请允许 1memos 安装未知来源应用，返回后将继续安装") }
            return
        }
        launchInstaller(File(state.downloadedApkPath))
    }

    fun onHostResumed() {
        if (
            installRequestedAfterPermission &&
            _uiState.value.phase == AppUpdatePhase.READY_TO_INSTALL &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            launchInstaller(File(_uiState.value.downloadedApkPath))
        }
    }

    private fun monitorDownload(
        downloadId: Long,
        apkFile: File,
        release: AppUpdateRelease,
    ) {
        downloadMonitorJob?.cancel()
        downloadMonitorJob =
            scope.launch {
                while (true) {
                    val snapshot =
                        runCatching { queryDownload(downloadId) }
                            .getOrElse { error ->
                                store.clearDownload()
                                _uiState.update {
                                    it.copy(
                                        phase = AppUpdatePhase.ERROR,
                                        promptVisible = false,
                                        statusMessage = "读取系统下载状态失败：${error.readableMessage()}",
                                    )
                                }
                                return@launch
                            }
                    when (snapshot.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            verifyDownloadedApk(apkFile, release)
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            store.clearDownload()
                            apkFile.delete()
                            _uiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.ERROR,
                                    promptVisible = false,
                                    downloadProgressPercent = null,
                                    statusMessage = "下载失败（系统错误 ${snapshot.reason}），请重试",
                                )
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_RUNNING,
                        -> {
                            _uiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.DOWNLOADING,
                                    release = release,
                                    downloadProgressPercent = snapshot.progressPercent,
                                    statusMessage =
                                        snapshot.progressPercent?.let { percent -> "正在下载：$percent%" }
                                            ?: "正在等待系统下载",
                                    downloadedApkPath = apkFile.absolutePath,
                                )
                            }
                        }
                        else -> {
                            store.clearDownload()
                            _uiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.ERROR,
                                    promptVisible = false,
                                    statusMessage = "系统找不到更新下载任务，请重新下载",
                                )
                            }
                            return@launch
                        }
                    }
                    delay(DOWNLOAD_POLL_INTERVAL_MS)
                }
            }
    }

    private suspend fun verifyDownloadedApk(
        apkFile: File,
        release: AppUpdateRelease,
    ) {
        runCatching {
            require(apkFile.isFile && apkFile.length() == release.apkSizeBytes) { "安装包大小不匹配" }
            require(apkFile.sha256() == release.sha256) { "安装包 SHA-256 校验失败" }

            val packageManager = context.packageManager
            val archiveInfo =
                requireNotNull(
                    packageManager.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                    ),
                ) { "无法读取安装包信息" }
            val currentInfo =
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            val metadata =
                DownloadedApkMetadata(
                    packageName = archiveInfo.packageName,
                    versionName = archiveInfo.versionName.orEmpty(),
                    versionCode = archiveInfo.longVersionCode,
                    signerSha256 = archiveInfo.signerSha256(),
                )
            when (
                validateDownloadedApkMetadata(
                    metadata = metadata,
                    expectedPackageName = BuildConfig.APPLICATION_ID,
                    expectedVersionName = release.versionName,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentSignerSha256 = currentInfo.signerSha256(),
                )
            ) {
                ApkValidationFailure.PACKAGE_NAME -> error("安装包包名不匹配")
                ApkValidationFailure.VERSION -> error("安装包版本与更新信息不匹配")
                ApkValidationFailure.SIGNATURE -> error("安装包签名与当前应用不一致")
                null -> Unit
            }
        }.onSuccess {
            _uiState.update {
                it.copy(
                    phase = AppUpdatePhase.READY_TO_INSTALL,
                    release = release,
                    promptVisible = true,
                    downloadProgressPercent = 100,
                    statusMessage = "更新已下载并通过安全校验",
                    downloadedApkPath = apkFile.absolutePath,
                )
            }
        }.onFailure { error ->
            store.clearDownload()
            apkFile.delete()
            _uiState.update {
                it.copy(
                    phase = AppUpdatePhase.ERROR,
                    promptVisible = false,
                    downloadProgressPercent = null,
                    statusMessage = "安装包校验失败：${error.readableMessage()}",
                )
            }
        }
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use DownloadSnapshot(status = 0, reason = 0, progressPercent = null)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else null
            DownloadSnapshot(status = status, reason = reason, progressPercent = progress)
        } ?: DownloadSnapshot(status = 0, reason = 0, progressPercent = null)
    }

    private fun launchInstaller(apkFile: File) {
        installRequestedAfterPermission = false
        val apkUri =
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile,
            )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_CONTENT_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        _uiState.update { it.copy(promptVisible = false, statusMessage = "已打开系统安装界面") }
    }

    private data class DownloadSnapshot(
        val status: Int,
        val reason: Int,
        val progressPercent: Int?,
    )

    private fun PackageInfo.signerSha256(): Set<String> {
        val info = signingInfo ?: return emptySet()
        val signers = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        return signers.mapTo(linkedSetOf()) { certificate -> certificate.toByteArray().sha256() }
    }

    private fun File.sha256(): String =
        FileInputStream(this).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHexString()
        }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun Throwable.readableMessage(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private companion object {
        const val REMIND_LATER_MS = 24 * 60 * 60 * 1_000L
        const val DOWNLOAD_POLL_INTERVAL_MS = 1_000L
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
