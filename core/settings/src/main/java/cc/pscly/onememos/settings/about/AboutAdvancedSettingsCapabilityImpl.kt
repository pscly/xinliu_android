package cc.pscly.onememos.settings.about

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import cc.pscly.onememos.diagnostics.DiagnosticsExportResult
import cc.pscly.onememos.diagnostics.DiagnosticsExporter
import cc.pscly.onememos.diagnostics.DiagnosticsFullSyncSnapshot
import cc.pscly.onememos.diagnostics.DiagnosticsPermissionSnapshot
import cc.pscly.onememos.diagnostics.DiagnosticsSettingsSnapshot
import cc.pscly.onememos.diagnostics.DiagnosticsSnapshot
import cc.pscly.onememos.diagnostics.DiagnosticsSyncSnapshot
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCapability
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCommand
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsResult
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsSnapshot
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.quicktiles.QuickTileKind
import cc.pscly.onememos.quicktiles.QuickTileRequestResult
import cc.pscly.onememos.quicktiles.QuickTileRequester
import cc.pscly.onememos.settings.SettingsCapabilityErrorMapper
import cc.pscly.onememos.update.AppIdentityPort
import cc.pscly.onememos.update.AppUpdateManager
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.worker.MemoDerivedFieldsRebuildScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * 关于与高级深能力：组合版本/更新/诊断/快捷开关/开发者选项。
 * 更新交付动作原样包装为 UpdateDelivery；tile 请求不经 SettingsPlatformAction。
 */
@Singleton
class AboutAdvancedSettingsCapabilityImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val syncStatusMonitor: SyncStatusMonitor,
    private val appUpdateManager: AppUpdateManager,
    private val diagnosticsExporter: DiagnosticsExporter,
    private val appIdentity: AppIdentityPort,
    private val diagnosticsIdentity: cc.pscly.onememos.diagnostics.AppIdentityPort,
    private val quickTileRequester: QuickTileRequester,
) : AboutAdvancedSettingsCapability {
    private val commandInFlight = MutableStateFlow<AboutAdvancedSettingsCommand?>(null)
    private val lastDiagnosticsUri = MutableStateFlow<String?>(null)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observe(): Flow<AboutAdvancedSettingsSnapshot> =
        combine(
            settingsRepository.settings,
            appUpdateManager.uiState,
            commandInFlight,
            lastDiagnosticsUri,
        ) { settings, update, inFlight, diagnosticsUri ->
            AboutAdvancedSettingsSnapshot(
                versionName = appIdentity.versionName,
                versionCode = appIdentity.versionCode,
                buildType = diagnosticsIdentity.buildType,
                update = mapUpdate(update),
                diagnosticsAvailable = diagnosticsUri != null,
                attachmentUploadLimitMb = settings.attachmentUploadMaxMb,
                developerOptions =
                    DeveloperOptions(
                        unlocked = settings.dev2Unlocked,
                        showPublicWorkspaceMemos = settings.dev2ShowPublicWorkspaceMemos,
                        autoTagLineKeywords = settings.devAutoTagLineKeywords,
                        showAutoTagLineInHome = settings.devShowAutoTagLineInHome,
                        showAutoTagLineInView = settings.devShowAutoTagLineInView,
                        showAutoTagLineInEdit = settings.devShowAutoTagLineInEdit,
                        homeRichPreviewStickyLimit = settings.devHomeRichPreviewStickyLimit,
                    ),
                commandInFlight = inFlight,
            )
        }

    override suspend fun execute(command: AboutAdvancedSettingsCommand): AboutAdvancedSettingsResult {
        val lock = locks.getOrPut(command.lockKey()) { Mutex() }
        if (!lock.tryLock()) {
            return AboutAdvancedSettingsResult.IgnoredDuplicate
        }
        commandInFlight.value = command
        return try {
            when (command) {
                AboutAdvancedSettingsCommand.CheckForUpdates -> {
                    appUpdateManager.checkForUpdates(manual = true)
                    AboutAdvancedSettingsResult.Success
                }
                AboutAdvancedSettingsCommand.DownloadUpdate -> {
                    appUpdateManager.startDownload()
                    AboutAdvancedSettingsResult.Success
                }
                AboutAdvancedSettingsCommand.InstallUpdate -> {
                    val action = appUpdateManager.requestDelivery()
                    if (action == null) {
                        AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                    } else {
                        AboutAdvancedSettingsResult.UpdateDelivery(action)
                    }
                }
                AboutAdvancedSettingsCommand.ClearIgnoredUpdate -> {
                    appUpdateManager.clearIgnoredVersion()
                    AboutAdvancedSettingsResult.Success
                }
                AboutAdvancedSettingsCommand.ExportDiagnostics -> {
                    val result = exportDiagnostics()
                    when (result) {
                        is DiagnosticsExportResult.Success -> {
                            lastDiagnosticsUri.value = result.fileUri
                            AboutAdvancedSettingsResult.Platform(
                                SettingsPlatformAction.ShareFile(
                                    uri = result.fileUri,
                                    mimeType = "application/json",
                                ),
                            )
                        }
                        is DiagnosticsExportResult.Failure ->
                            AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
                    }
                }
                AboutAdvancedSettingsCommand.RequestQuickCaptureTile ->
                    mapTileResult(quickTileRequester.request(QuickTileKind.QUICK_CAPTURE))
                AboutAdvancedSettingsCommand.RequestScreenshotTile ->
                    mapTileResult(quickTileRequester.request(QuickTileKind.SCREENSHOT_CAPTURE))
                AboutAdvancedSettingsCommand.OpenQuickCapture ->
                    AboutAdvancedSettingsResult.Platform(SettingsPlatformAction.OpenQuickCapture)
                AboutAdvancedSettingsCommand.OpenScreenshotCapture ->
                    AboutAdvancedSettingsResult.Platform(SettingsPlatformAction.OpenScreenshotCapture)
                AboutAdvancedSettingsCommand.RebuildDerivedFields -> {
                    MemoDerivedFieldsRebuildScheduler.enqueue(
                        context = context,
                        initialDelaySeconds = 0,
                    )
                    AboutAdvancedSettingsResult.Success
                }
                is AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb -> {
                    if (command.value < 0) {
                        return AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                    }
                    settingsRepository.setAttachmentUploadMaxMb(command.value)
                    AboutAdvancedSettingsResult.Success
                }
                is AboutAdvancedSettingsCommand.SetDeveloperOptions -> {
                    val o = command.options
                    settingsRepository.setDev2Unlocked(o.unlocked)
                    settingsRepository.setDev2ShowPublicWorkspaceMemos(o.showPublicWorkspaceMemos)
                    settingsRepository.setDevAutoTagLineKeywords(o.autoTagLineKeywords)
                    settingsRepository.setDevShowAutoTagLineInHome(o.showAutoTagLineInHome)
                    settingsRepository.setDevShowAutoTagLineInView(o.showAutoTagLineInView)
                    settingsRepository.setDevShowAutoTagLineInEdit(o.showAutoTagLineInEdit)
                    settingsRepository.setDevHomeRichPreviewStickyLimit(o.homeRichPreviewStickyLimit)
                    AboutAdvancedSettingsResult.Success
                }
            }
        } catch (t: Throwable) {
            val mapped = SettingsCapabilityErrorMapper.map(t)
            AboutAdvancedSettingsResult.Failure(
                when (mapped) {
                    is SettingsCapabilityError.Unknown -> SettingsCapabilityError.StorageFailure
                    else -> mapped
                },
            )
        } finally {
            commandInFlight.value = null
            lock.unlock()
        }
    }

    private fun mapUpdate(state: cc.pscly.onememos.update.AppUpdateUiState): UpdateSettingsSnapshot {
        val phase =
            when (state.phase) {
                AppUpdatePhase.IDLE -> UpdateSettingsPhase.IDLE
                AppUpdatePhase.CHECKING -> UpdateSettingsPhase.CHECKING
                AppUpdatePhase.AVAILABLE -> UpdateSettingsPhase.AVAILABLE
                AppUpdatePhase.DOWNLOADING -> UpdateSettingsPhase.DOWNLOADING
                AppUpdatePhase.READY_TO_INSTALL -> UpdateSettingsPhase.READY_TO_INSTALL
                AppUpdatePhase.UP_TO_DATE -> UpdateSettingsPhase.UP_TO_DATE
                AppUpdatePhase.ERROR -> UpdateSettingsPhase.ERROR
            }
        return UpdateSettingsSnapshot(
            phase = phase,
            availableVersion = state.release?.versionName,
            ignoredVersionTag = state.ignoredVersionTag.ifBlank { null },
            downloadProgressPercent = state.downloadProgressPercent,
            error =
                if (phase == UpdateSettingsPhase.ERROR) {
                    SettingsCapabilityError.NetworkUnavailable
                } else {
                    null
                },
        )
    }

    private fun mapTileResult(result: QuickTileRequestResult): AboutAdvancedSettingsResult =
        when (result) {
            is QuickTileRequestResult.Completed -> AboutAdvancedSettingsResult.Success
            QuickTileRequestResult.PlatformUnavailable ->
                AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable)
        }

    private suspend fun exportDiagnostics(): DiagnosticsExportResult {
        val settings = settingsRepository.settings.first()
        val global = syncStatusMonitor.globalState.first()
        val notificationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val calendarReadGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        val calendarWriteGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        val ignoringBattery =
            runCatching {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }.getOrDefault(false)
        val canScheduleExact =
            runCatching {
                val am = context.getSystemService(android.app.AlarmManager::class.java)
                am?.canScheduleExactAlarms() == true
            }.getOrDefault(false)

        val snapshot =
            DiagnosticsSnapshot(
                generatedAtEpochMs = System.currentTimeMillis(),
                permissions =
                    DiagnosticsPermissionSnapshot(
                        postNotificationsGranted = notificationGranted,
                        readCalendarGranted = calendarReadGranted,
                        writeCalendarGranted = calendarWriteGranted,
                        canDrawOverlays = Settings.canDrawOverlays(context),
                        canScheduleExactAlarms = canScheduleExact,
                        ignoringBatteryOptimizations = ignoringBattery,
                    ),
                settings =
                    DiagnosticsSettingsSnapshot(
                        serverUrl = settings.serverUrl,
                        tokenSet = settings.token.isNotBlank(),
                        loginMode = settings.loginMode.name,
                        dev2Unlocked = settings.dev2Unlocked,
                        dev2ShowPublicWorkspaceMemos = settings.dev2ShowPublicWorkspaceMemos,
                        themePalette = settings.themePalette.name,
                        themeMode = settings.themeMode.name,
                        defaultVisibility = settings.defaultVisibility.name,
                        regexSearchEnabled = settings.regexSearchEnabled,
                        showTagCountsInFilter = settings.showTagCountsInFilter,
                        quickCaptureOverlayEnabled = settings.quickCaptureOverlayEnabled,
                        quickInsertTimeEnabled = settings.quickInsertTimeEnabled,
                        quickInsertTimeFormat = settings.quickInsertTimeFormat.name,
                        sealStampDurationMs = settings.sealStampDurationMs,
                        offlineImagePrefetchEnabled = settings.offlineImagePrefetchEnabled,
                        offlineImagePrefetchMaxMemos = settings.offlineImagePrefetchMaxMemos,
                        offlineImagePrefetchMaxImages = settings.offlineImagePrefetchMaxImages,
                        attachmentCacheMaxMb = settings.attachmentCacheMaxMb,
                        attachmentUploadMaxMb = settings.attachmentUploadMaxMb,
                        todoReminderMode = settings.todoReminderMode.name,
                        calendarIntegrationEnabled = settings.calendarIntegrationEnabled,
                        calendarIntegrationCalendarId = settings.calendarIntegrationCalendarId ?: 0L,
                        calendarIntegrationSyncReminders = settings.calendarIntegrationSyncReminders,
                    ),
                sync =
                    DiagnosticsSyncSnapshot(
                        workState = global.workState.name,
                        pendingCount = global.pendingCount,
                        networkOnline = global.networkOnline,
                        lastSuccessAt = global.lastSuccessAt,
                        lastError = global.lastError,
                        lastErrorAt = global.lastErrorAt,
                        lastErrorHttpCode = global.lastErrorHttpCode,
                        authInvalid = global.authInvalid,
                    ),
                fullSync =
                    DiagnosticsFullSyncSnapshot(
                        status = settings.fullSync.status.name,
                        runId = settings.fullSync.runId,
                        stage = settings.fullSync.stage.name,
                        pagesFetched = settings.fullSync.pagesFetched,
                        itemsFetched = settings.fullSync.itemsFetched,
                        lastSuccessAt = settings.fullSync.lastSuccessAt,
                        lastError = settings.fullSync.lastError,
                        key = settings.fullSync.syncKey,
                    ),
            )
        return diagnosticsExporter.export(snapshot)
    }

    private fun AboutAdvancedSettingsCommand.lockKey(): String =
        when (this) {
            AboutAdvancedSettingsCommand.CheckForUpdates -> "CheckForUpdates"
            AboutAdvancedSettingsCommand.DownloadUpdate -> "DownloadUpdate"
            AboutAdvancedSettingsCommand.InstallUpdate -> "InstallUpdate"
            AboutAdvancedSettingsCommand.ClearIgnoredUpdate -> "ClearIgnoredUpdate"
            AboutAdvancedSettingsCommand.ExportDiagnostics -> "ExportDiagnostics"
            AboutAdvancedSettingsCommand.RequestQuickCaptureTile -> "RequestQuickCaptureTile"
            AboutAdvancedSettingsCommand.RequestScreenshotTile -> "RequestScreenshotTile"
            AboutAdvancedSettingsCommand.OpenQuickCapture -> "OpenQuickCapture"
            AboutAdvancedSettingsCommand.OpenScreenshotCapture -> "OpenScreenshotCapture"
            AboutAdvancedSettingsCommand.RebuildDerivedFields -> "RebuildDerivedFields"
            is AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb -> "SetAttachmentUploadLimitMb"
            is AboutAdvancedSettingsCommand.SetDeveloperOptions -> "SetDeveloperOptions"
        }
}
