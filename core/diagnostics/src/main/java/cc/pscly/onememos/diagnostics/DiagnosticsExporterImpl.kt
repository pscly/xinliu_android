package cc.pscly.onememos.diagnostics

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class DiagnosticsExporterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appIdentity: AppIdentityPort,
) : DiagnosticsExporter {
    override suspend fun export(snapshot: DiagnosticsSnapshot): DiagnosticsExportResult {
        return runCatching {
            val ts =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(snapshot.generatedAtEpochMs))
            val root =
                JSONObject()
                    .put("generatedAt", ts)
                    .put("generatedAtEpochMs", snapshot.generatedAtEpochMs)
                    .put(
                        "app",
                        JSONObject()
                            .put("packageName", appIdentity.applicationId)
                            .put("versionName", appIdentity.versionName)
                            .put("versionCode", appIdentity.versionCode)
                            .put("buildType", appIdentity.buildType)
                            .put("flowBackendBaseUrl", appIdentity.flowBackendBaseUrl),
                    )
                    .put(
                        "device",
                        JSONObject()
                            .put("manufacturer", Build.MANUFACTURER)
                            .put("brand", Build.BRAND)
                            .put("model", Build.MODEL)
                            .put("device", Build.DEVICE)
                            .put("product", Build.PRODUCT)
                            .put("sdkInt", Build.VERSION.SDK_INT)
                            .put("release", Build.VERSION.RELEASE),
                    )
                    .put(
                        "permissions",
                        JSONObject()
                            .put("postNotificationsGranted", snapshot.permissions.postNotificationsGranted)
                            .put("readCalendarGranted", snapshot.permissions.readCalendarGranted)
                            .put("writeCalendarGranted", snapshot.permissions.writeCalendarGranted)
                            .put("canDrawOverlays", snapshot.permissions.canDrawOverlays)
                            .put("canScheduleExactAlarms", snapshot.permissions.canScheduleExactAlarms)
                            .put("ignoringBatteryOptimizations", snapshot.permissions.ignoringBatteryOptimizations),
                    )
                    .put(
                        "settings",
                        JSONObject()
                            .put("serverUrl", snapshot.settings.serverUrl)
                            .put("tokenSet", snapshot.settings.tokenSet)
                            .put("loginMode", snapshot.settings.loginMode)
                            .put("dev2Unlocked", snapshot.settings.dev2Unlocked)
                            .put("dev2ShowPublicWorkspaceMemos", snapshot.settings.dev2ShowPublicWorkspaceMemos)
                            .put("themePalette", snapshot.settings.themePalette)
                            .put("themeMode", snapshot.settings.themeMode)
                            .put("defaultVisibility", snapshot.settings.defaultVisibility)
                            .put("regexSearchEnabled", snapshot.settings.regexSearchEnabled)
                            .put("showTagCountsInFilter", snapshot.settings.showTagCountsInFilter)
                            .put("quickCaptureOverlayEnabled", snapshot.settings.quickCaptureOverlayEnabled)
                            .put("quickInsertTimeEnabled", snapshot.settings.quickInsertTimeEnabled)
                            .put("quickInsertTimeFormat", snapshot.settings.quickInsertTimeFormat)
                            .put("sealStampDurationMs", snapshot.settings.sealStampDurationMs)
                            .put("offlineImagePrefetchEnabled", snapshot.settings.offlineImagePrefetchEnabled)
                            .put("offlineImagePrefetchMaxMemos", snapshot.settings.offlineImagePrefetchMaxMemos)
                            .put("offlineImagePrefetchMaxImages", snapshot.settings.offlineImagePrefetchMaxImages)
                            .put("attachmentCacheMaxMb", snapshot.settings.attachmentCacheMaxMb)
                            .put("attachmentUploadMaxMb", snapshot.settings.attachmentUploadMaxMb)
                            .put("todoReminderMode", snapshot.settings.todoReminderMode)
                            .put("calendarIntegrationEnabled", snapshot.settings.calendarIntegrationEnabled)
                            .put("calendarIntegrationCalendarId", snapshot.settings.calendarIntegrationCalendarId)
                            .put("calendarIntegrationSyncReminders", snapshot.settings.calendarIntegrationSyncReminders)
                            .put(
                                "sync",
                                JSONObject()
                                    .put("workState", snapshot.sync.workState)
                                    .put("pendingCount", snapshot.sync.pendingCount)
                                    .put("networkOnline", snapshot.sync.networkOnline)
                                    .put("lastSuccessAt", snapshot.sync.lastSuccessAt)
                                    .put("lastError", snapshot.sync.lastError)
                                    .put("lastErrorAt", snapshot.sync.lastErrorAt)
                                    .put("lastErrorHttpCode", snapshot.sync.lastErrorHttpCode)
                                    .put("authInvalid", snapshot.sync.authInvalid),
                            )
                            .put(
                                "fullSync",
                                JSONObject()
                                    .put("status", snapshot.fullSync.status)
                                    .put("runId", snapshot.fullSync.runId)
                                    .put("stage", snapshot.fullSync.stage)
                                    .put("pagesFetched", snapshot.fullSync.pagesFetched)
                                    .put("itemsFetched", snapshot.fullSync.itemsFetched)
                                    .put("lastSuccessAt", snapshot.fullSync.lastSuccessAt)
                                    .put("lastError", snapshot.fullSync.lastError)
                                    .put("key", snapshot.fullSync.key),
                            ),
                    )

            val sharedDir = File(context.filesDir, "shared").apply { mkdirs() }
            val out = File(sharedDir, "diagnostics-$ts.json")
            out.writeText(root.toString(2), Charsets.UTF_8)
            val uri =
                FileProvider.getUriForFile(context, appIdentity.fileProviderAuthority, out)
                    ?: return@runCatching DiagnosticsExportResult.Failure(DiagnosticsError.PROVIDER_FAILED)
            DiagnosticsExportResult.Success(fileUri = uri.toString())
        }.getOrElse {
            DiagnosticsExportResult.Failure(DiagnosticsError.WRITE_FAILED)
        }
    }
}
