package cc.pscly.onememos.diagnostics

data class DiagnosticsSnapshot(
    val generatedAtEpochMs: Long,
    val permissions: DiagnosticsPermissionSnapshot,
    val settings: DiagnosticsSettingsSnapshot,
    val sync: DiagnosticsSyncSnapshot,
    val fullSync: DiagnosticsFullSyncSnapshot,
)

data class DiagnosticsPermissionSnapshot(
    val postNotificationsGranted: Boolean,
    val readCalendarGranted: Boolean,
    val writeCalendarGranted: Boolean,
    val canDrawOverlays: Boolean,
    val canScheduleExactAlarms: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

data class DiagnosticsSettingsSnapshot(
    val serverUrl: String,
    val tokenSet: Boolean,
    val loginMode: String,
    val dev2Unlocked: Boolean,
    val dev2ShowPublicWorkspaceMemos: Boolean,
    val themePalette: String,
    val themeMode: String,
    val defaultVisibility: String,
    val regexSearchEnabled: Boolean,
    val showTagCountsInFilter: Boolean,
    val quickCaptureOverlayEnabled: Boolean,
    val quickInsertTimeEnabled: Boolean,
    val quickInsertTimeFormat: String,
    val sealStampDurationMs: Int,
    val offlineImagePrefetchEnabled: Boolean,
    val offlineImagePrefetchMaxMemos: Int,
    val offlineImagePrefetchMaxImages: Int,
    val attachmentCacheMaxMb: Int,
    val attachmentUploadMaxMb: Int,
    val todoReminderMode: String,
    val calendarIntegrationEnabled: Boolean,
    val calendarIntegrationCalendarId: Long,
    val calendarIntegrationSyncReminders: Boolean,
)

data class DiagnosticsSyncSnapshot(
    val workState: String,
    val pendingCount: Int,
    val networkOnline: Boolean,
    val lastSuccessAt: Long,
    val lastError: String,
    val lastErrorAt: Long,
    val lastErrorHttpCode: Int?,
    val authInvalid: Boolean,
)

data class DiagnosticsFullSyncSnapshot(
    val status: String,
    val runId: String,
    val stage: String,
    val pagesFetched: Int,
    val itemsFetched: Int,
    val lastSuccessAt: Long,
    val lastError: String,
    val key: String,
)
