package cc.pscly.onememos.ui.feature.settings

import android.Manifest
import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 迁移期适配：把旧 SettingsUiState 复制为独立 DiagnosticsSnapshot 后调用 exporter。
 * Task 31 会删除本类与旧 Screen/ViewModel。
 */
@Singleton
class LegacyDiagnosticsAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: DiagnosticsExporter,
) {
    suspend fun export(
        uiState: SettingsUiState,
        appInfo: SettingsAppInfo,
        canDrawOverlays: Boolean,
        canScheduleExactAlarms: Boolean,
    ): DiagnosticsExportResult {
        val notificationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val calendarReadGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        val calendarWriteGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        val ignoringBatteryOptimizations =
            runCatching {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }.getOrElse { false }
        val overlayGranted =
            canDrawOverlays || Settings.canDrawOverlays(context)

        val snapshot =
            DiagnosticsSnapshot(
                generatedAtEpochMs = System.currentTimeMillis(),
                permissions =
                    DiagnosticsPermissionSnapshot(
                        postNotificationsGranted = notificationGranted,
                        readCalendarGranted = calendarReadGranted,
                        writeCalendarGranted = calendarWriteGranted,
                        canDrawOverlays = overlayGranted,
                        canScheduleExactAlarms = canScheduleExactAlarms,
                        ignoringBatteryOptimizations = ignoringBatteryOptimizations,
                    ),
                settings =
                    DiagnosticsSettingsSnapshot(
                        serverUrl = uiState.serverUrl,
                        tokenSet = uiState.token.isNotBlank(),
                        loginMode = uiState.loginMode.name,
                        dev2Unlocked = uiState.dev2Unlocked,
                        dev2ShowPublicWorkspaceMemos = uiState.dev2ShowPublicWorkspaceMemos,
                        themePalette = uiState.themePalette.name,
                        themeMode = uiState.themeMode.name,
                        defaultVisibility = uiState.defaultVisibility.name,
                        regexSearchEnabled = uiState.regexSearchEnabled,
                        showTagCountsInFilter = uiState.showTagCountsInFilter,
                        quickCaptureOverlayEnabled = uiState.quickCaptureOverlayEnabled,
                        quickInsertTimeEnabled = uiState.quickInsertTimeEnabled,
                        quickInsertTimeFormat = uiState.quickInsertTimeFormat.name,
                        sealStampDurationMs = uiState.sealStampDurationMs,
                        offlineImagePrefetchEnabled = uiState.offlineImagePrefetchEnabled,
                        offlineImagePrefetchMaxMemos = uiState.offlineImagePrefetchMaxMemos,
                        offlineImagePrefetchMaxImages = uiState.offlineImagePrefetchMaxImages,
                        attachmentCacheMaxMb = uiState.attachmentCacheMaxMb,
                        attachmentUploadMaxMb = uiState.attachmentUploadMaxMb,
                        todoReminderMode = uiState.todoReminderMode.name,
                        calendarIntegrationEnabled = uiState.calendarIntegrationEnabled,
                        calendarIntegrationCalendarId = uiState.calendarIntegrationCalendarId ?: 0L,
                        calendarIntegrationSyncReminders = uiState.calendarIntegrationSyncReminders,
                    ),
                sync =
                    DiagnosticsSyncSnapshot(
                        workState = uiState.globalSync.workState.name,
                        pendingCount = uiState.globalSync.pendingCount,
                        networkOnline = uiState.globalSync.networkOnline,
                        lastSuccessAt = uiState.globalSync.lastSuccessAt,
                        lastError = uiState.globalSync.lastError,
                        lastErrorAt = uiState.globalSync.lastErrorAt,
                        lastErrorHttpCode = uiState.globalSync.lastErrorHttpCode,
                        authInvalid = uiState.globalSync.authInvalid,
                    ),
                fullSync =
                    DiagnosticsFullSyncSnapshot(
                        status = uiState.fullSyncStatus.name,
                        runId = uiState.fullSyncRunId,
                        stage = uiState.fullSyncStage.name,
                        pagesFetched = uiState.fullSyncPagesFetched,
                        itemsFetched = uiState.fullSyncItemsFetched,
                        lastSuccessAt = uiState.fullSyncLastSuccessAt,
                        lastError = uiState.fullSyncLastError,
                        key = uiState.fullSyncKey,
                    ),
            )
        // appInfo 由 Diagnostics AppIdentityPort 提供，避免 Feature 写 BuildConfig。
        return exporter.export(snapshot)
    }
}
