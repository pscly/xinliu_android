package cc.pscly.onememos.diagnostics

import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsExporterTest {
    @Test
    fun export_writesSharedDiagnosticsWithoutTokenOrPassword() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val identity =
            object : AppIdentityPort {
                override val applicationId: String = "cc.pscly.onememos"
                override val versionName: String = "1.8.11"
                override val versionCode: Long = 156L
                override val buildType: String = "debug"
                override val flowBackendBaseUrl: String = "https://xl.pscly.cc/"
                override val fileProviderAuthority: String = "cc.pscly.onememos.fileprovider"
            }
        // Robolectric 下 FileProvider 可能缺 authority 配置；先验证 JSON 文件内容与路径语义。
        val exporter =
            object : DiagnosticsExporter {
                override suspend fun export(snapshot: DiagnosticsSnapshot): DiagnosticsExportResult {
                    val impl = DiagnosticsExporterImpl(context, identity)
                    // 若 PROVIDER 失败，仍校验 shared 目录文件与敏感字段。
                    val result = impl.export(snapshot)
                    val shared = File(context.filesDir, "shared")
                    val files = shared.listFiles()?.filter { it.name.startsWith("diagnostics-") }.orEmpty()
                    assertTrue(files.isNotEmpty())
                    val body = files.maxBy { it.lastModified() }.readText()
                    assertTrue(body.contains("\"tokenSet\": true"))
                    assertFalse(body.contains("secret-token"))
                    assertFalse(body.contains("password"))
                    assertTrue(body.contains("\"versionName\": \"1.8.11\""))
                    return result
                }
            }

        val snapshot =
            DiagnosticsSnapshot(
                generatedAtEpochMs = 1_721_000_000_000L,
                permissions =
                    DiagnosticsPermissionSnapshot(
                        postNotificationsGranted = true,
                        readCalendarGranted = false,
                        writeCalendarGranted = false,
                        canDrawOverlays = true,
                        canScheduleExactAlarms = false,
                        ignoringBatteryOptimizations = true,
                    ),
                settings =
                    DiagnosticsSettingsSnapshot(
                        serverUrl = "https://example.com",
                        tokenSet = true,
                        loginMode = "BACKEND",
                        dev2Unlocked = false,
                        dev2ShowPublicWorkspaceMemos = false,
                        themePalette = "PAPER_INK",
                        themeMode = "FOLLOW_SYSTEM",
                        defaultVisibility = "PRIVATE",
                        regexSearchEnabled = false,
                        showTagCountsInFilter = true,
                        quickCaptureOverlayEnabled = false,
                        quickInsertTimeEnabled = false,
                        quickInsertTimeFormat = "FULL_DATETIME",
                        sealStampDurationMs = 600,
                        offlineImagePrefetchEnabled = true,
                        offlineImagePrefetchMaxMemos = 30,
                        offlineImagePrefetchMaxImages = 60,
                        attachmentCacheMaxMb = 1024,
                        attachmentUploadMaxMb = 50,
                        todoReminderMode = "SMART",
                        calendarIntegrationEnabled = false,
                        calendarIntegrationCalendarId = 0L,
                        calendarIntegrationSyncReminders = true,
                    ),
                sync =
                    DiagnosticsSyncSnapshot(
                        workState = "IDLE",
                        pendingCount = 0,
                        networkOnline = true,
                        lastSuccessAt = 0L,
                        lastError = "",
                        lastErrorAt = 0L,
                        lastErrorHttpCode = null,
                        authInvalid = false,
                    ),
                fullSync =
                    DiagnosticsFullSyncSnapshot(
                        status = "IDLE",
                        runId = "",
                        stage = "NORMAL",
                        pagesFetched = 0,
                        itemsFetched = 0,
                        lastSuccessAt = 0L,
                        lastError = "",
                        key = "",
                    ),
            )

        exporter.export(snapshot)
        // same-second overwrite semantics: second export reuses same timestamp path pattern
        exporter.export(snapshot)
        val files =
            File(context.filesDir, "shared")
                .listFiles()
                ?.filter { it.name.startsWith("diagnostics-") }
                .orEmpty()
        assertTrue(files.isNotEmpty())
        // 同一 generatedAtEpochMs 覆盖同名文件
        assertEquals(1, files.map { it.name }.toSet().size)
    }
}
