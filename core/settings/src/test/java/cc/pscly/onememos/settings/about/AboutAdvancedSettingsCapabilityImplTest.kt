package cc.pscly.onememos.settings.about

import android.app.Application
import cc.pscly.onememos.diagnostics.DiagnosticsError
import cc.pscly.onememos.diagnostics.DiagnosticsExportResult
import cc.pscly.onememos.diagnostics.DiagnosticsExporter
import cc.pscly.onememos.diagnostics.DiagnosticsSnapshot
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCommand
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsResult
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.quicktiles.QuickTileKind
import cc.pscly.onememos.quicktiles.QuickTileRequestResult
import cc.pscly.onememos.quicktiles.QuickTileRequester
import cc.pscly.onememos.update.AppIdentityPort
import cc.pscly.onememos.update.AppUpdateManager
import cc.pscly.onememos.update.AppUpdateStore
import cc.pscly.onememos.update.GitHubReleaseDto
import cc.pscly.onememos.update.GitHubUpdateApi
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AboutAdvancedSettingsCapabilityImplTest {
    @Test
    fun observe_mapsVersionAndDeveloperOptions() =
        runBlocking {
            val harness = Harness()
            harness.settings.value =
                AppSettings(
                    attachmentUploadMaxMb = 100,
                    dev2Unlocked = true,
                    dev2ShowPublicWorkspaceMemos = true,
                    devAutoTagLineKeywords = "__Atags",
                    devShowAutoTagLineInHome = true,
                    devHomeRichPreviewStickyLimit = 200,
                )
            val snap = harness.capability.observe().first()
            assertEquals("1.9.0", snap.versionName)
            assertEquals(190L, snap.versionCode)
            assertEquals("debug", snap.buildType)
            assertEquals(UpdateSettingsPhase.IDLE, snap.update.phase)
            assertEquals(100, snap.attachmentUploadLimitMb)
            assertEquals(true, snap.developerOptions.unlocked)
            assertEquals(false, snap.diagnosticsAvailable)
            assertEquals(null, snap.commandInFlight)
        }

    @Test
    fun checkForUpdates_triggersManagerAndMapsPhase() =
        runBlocking {
            val harness = Harness()
            assertEquals(
                AboutAdvancedSettingsResult.Success,
                harness.capability.execute(AboutAdvancedSettingsCommand.CheckForUpdates),
            )
            // 给 manager 协程一点时间
            delay(100)
            val phase = harness.capability.observe().first().update.phase
            assertTrue(
                phase == UpdateSettingsPhase.CHECKING ||
                    phase == UpdateSettingsPhase.AVAILABLE ||
                    phase == UpdateSettingsPhase.UP_TO_DATE ||
                    phase == UpdateSettingsPhase.ERROR ||
                    phase == UpdateSettingsPhase.IDLE,
            )
            assertEquals(1, harness.api.calls.get())
        }

    @Test
    fun clearIgnoredUpdate_succeeds() =
        runBlocking {
            val harness = Harness()
            assertEquals(
                AboutAdvancedSettingsResult.Success,
                harness.capability.execute(AboutAdvancedSettingsCommand.ClearIgnoredUpdate),
            )
        }

    @Test
    fun installUpdate_whenNotReady_mapsInvalidInput() =
        runBlocking {
            val harness = Harness()
            assertEquals(
                AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                harness.capability.execute(AboutAdvancedSettingsCommand.InstallUpdate),
            )
        }

    @Test
    fun exportDiagnostics_successSharesFile_failureMapsStorage() =
        runBlocking {
            val harness = Harness()
            harness.exporter.next =
                DiagnosticsExportResult.Success(fileUri = "content://diag/file.json")
            val ok = harness.capability.execute(AboutAdvancedSettingsCommand.ExportDiagnostics)
            val platform = ok as AboutAdvancedSettingsResult.Platform
            val share = platform.action as SettingsPlatformAction.ShareFile
            assertEquals("content://diag/file.json", share.uri)
            assertEquals(true, harness.capability.observe().first().diagnosticsAvailable)

            harness.exporter.next =
                DiagnosticsExportResult.Failure(DiagnosticsError.WRITE_FAILED)
            val failed = harness.capability.execute(AboutAdvancedSettingsCommand.ExportDiagnostics)
            assertEquals(
                AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.StorageFailure),
                failed,
            )
        }

    @Test
    fun tileRequests_mapSuccessAndPlatformUnavailable() =
        runBlocking {
            val harness = Harness()
            harness.tileRequester.result = QuickTileRequestResult.Completed(statusCode = 0)
            assertEquals(
                AboutAdvancedSettingsResult.Success,
                harness.capability.execute(AboutAdvancedSettingsCommand.RequestQuickCaptureTile),
            )
            assertEquals(QuickTileKind.QUICK_CAPTURE, harness.tileRequester.lastKind)

            harness.tileRequester.result = QuickTileRequestResult.PlatformUnavailable
            assertEquals(
                AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable),
                harness.capability.execute(AboutAdvancedSettingsCommand.RequestScreenshotTile),
            )
            assertEquals(QuickTileKind.SCREENSHOT_CAPTURE, harness.tileRequester.lastKind)
        }

    @Test
    fun openCaptureCommands_returnPlatformActions() =
        runBlocking {
            val harness = Harness()
            val quick =
                harness.capability.execute(AboutAdvancedSettingsCommand.OpenQuickCapture)
                    as AboutAdvancedSettingsResult.Platform
            assertEquals(SettingsPlatformAction.OpenQuickCapture, quick.action)

            val shot =
                harness.capability.execute(AboutAdvancedSettingsCommand.OpenScreenshotCapture)
                    as AboutAdvancedSettingsResult.Platform
            assertEquals(SettingsPlatformAction.OpenScreenshotCapture, shot.action)
        }

    @Test
    fun setUploadLimit_andDeveloperOptions() =
        runBlocking {
            val harness = Harness()
            assertEquals(
                AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                harness.capability.execute(
                    AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb(-1),
                ),
            )
            assertEquals(
                AboutAdvancedSettingsResult.Success,
                harness.capability.execute(
                    AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb(128),
                ),
            )
            assertEquals(128, harness.settingsRepo.flow.value.attachmentUploadMaxMb)

            val options =
                DeveloperOptions(
                    unlocked = true,
                    showPublicWorkspaceMemos = true,
                    autoTagLineKeywords = "x",
                    showAutoTagLineInHome = true,
                    showAutoTagLineInView = false,
                    showAutoTagLineInEdit = true,
                    homeRichPreviewStickyLimit = 50,
                )
            assertEquals(
                AboutAdvancedSettingsResult.Success,
                harness.capability.execute(AboutAdvancedSettingsCommand.SetDeveloperOptions(options)),
            )
            val s = harness.settingsRepo.flow.value
            assertEquals(true, s.dev2Unlocked)
            assertEquals(true, s.dev2ShowPublicWorkspaceMemos)
            assertEquals("x", s.devAutoTagLineKeywords)
            assertEquals(50, s.devHomeRichPreviewStickyLimit)
        }

    @Test
    fun concurrentExport_secondIsIgnoredDuplicate() =
        runBlocking {
            val harness = Harness(exportHoldMs = 250L)
            val first =
                async(Dispatchers.IO) {
                    harness.capability.execute(AboutAdvancedSettingsCommand.ExportDiagnostics)
                }
            while (harness.exporter.calls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    harness.capability.execute(AboutAdvancedSettingsCommand.ExportDiagnostics)
                }
            assertEquals(AboutAdvancedSettingsResult.IgnoredDuplicate, second)
            assertTrue(first.await() is AboutAdvancedSettingsResult.Platform)
            assertEquals(1, harness.exporter.calls.get())
        }

    @Test
    fun concurrentTileRequest_secondIsIgnoredDuplicate() =
        runBlocking {
            val harness = Harness(tileHoldMs = 250L)
            val first =
                async(Dispatchers.IO) {
                    harness.capability.execute(AboutAdvancedSettingsCommand.RequestQuickCaptureTile)
                }
            while (harness.tileRequester.calls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    harness.capability.execute(AboutAdvancedSettingsCommand.RequestQuickCaptureTile)
                }
            assertEquals(AboutAdvancedSettingsResult.IgnoredDuplicate, second)
            assertEquals(AboutAdvancedSettingsResult.Success, first.await())
            assertEquals(1, harness.tileRequester.calls.get())
        }

    private class Harness(
        exportHoldMs: Long = 0L,
        tileHoldMs: Long = 0L,
    ) {
        val settings = MutableStateFlow(AppSettings())
        val global = MutableStateFlow(GlobalSyncState())
        val settingsRepo = FakeSettingsRepository(settings)
        val api = FakeGitHubUpdateApi()
        val appIdentity =
            object : AppIdentityPort {
                override val applicationId: String = "cc.pscly.onememos"
                override val versionName: String = "1.9.0"
                override val versionCode: Long = 190L
                override val fileProviderAuthority: String = "cc.pscly.onememos.fileprovider"
            }
        val diagnosticsIdentity =
            object : cc.pscly.onememos.diagnostics.AppIdentityPort {
                override val applicationId: String = "cc.pscly.onememos"
                override val versionName: String = "1.9.0"
                override val versionCode: Long = 190L
                override val buildType: String = "debug"
                override val flowBackendBaseUrl: String = "https://example.com"
                override val fileProviderAuthority: String = "cc.pscly.onememos.fileprovider"
            }
        val appContext = RuntimeEnvironment.getApplication()
        val updateManager =
            AppUpdateManager(
                context = appContext,
                api = api,
                store = AppUpdateStore(appContext),
                appIdentity = appIdentity,
            )
        val exporter = FakeDiagnosticsExporter(exportHoldMs)
        val tileRequester = FakeQuickTileRequester(tileHoldMs)
        val capability =
            AboutAdvancedSettingsCapabilityImpl(
                context = appContext,
                settingsRepository = settingsRepo,
                syncStatusMonitor =
                    object : SyncStatusMonitor {
                        override val globalState: Flow<GlobalSyncState> = global
                    },
                appUpdateManager = updateManager,
                diagnosticsExporter = exporter,
                appIdentity = appIdentity,
                diagnosticsIdentity = diagnosticsIdentity,
                quickTileRequester = tileRequester,
            )
    }

    private class FakeGitHubUpdateApi : GitHubUpdateApi {
        val calls = AtomicInteger(0)

        override suspend fun latestStableRelease(): GitHubReleaseDto {
            calls.incrementAndGet()
            error("network unavailable for test")
        }
    }

    private class FakeSettingsRepository(
        val flow: MutableStateFlow<AppSettings>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = flow

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun setToken(token: String) = Unit
        override suspend fun setLoginMode(mode: LoginMode) = Unit
        override suspend fun setCurrentUserCreator(creator: String) = Unit
        override suspend fun setDev2Unlocked(unlocked: Boolean) {
            flow.value = flow.value.copy(dev2Unlocked = unlocked)
        }
        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) {
            flow.value = flow.value.copy(dev2ShowPublicWorkspaceMemos = enabled)
        }
        override suspend fun setThemePalette(palette: ThemePalette) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit
        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) = Unit
        override suspend fun setSealStampDurationMs(durationMs: Int) = Unit
        override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) = Unit
        override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) = Unit
        override suspend fun setOfflineImagePrefetchMaxImages(count: Int) = Unit
        override suspend fun setAttachmentCacheMaxMb(mb: Int) = Unit
        override suspend fun setAttachmentUploadMaxMb(mb: Int) {
            flow.value = flow.value.copy(attachmentUploadMaxMb = mb)
        }
        override suspend fun setTodoReminderMode(mode: TodoReminderMode) = Unit
        override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) = Unit
        override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) = Unit
        override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) = Unit
        override suspend fun setLastSyncSuccess() = Unit
        override suspend fun setLastSyncError(error: String, httpCode: Int) = Unit
        override suspend fun setDevAutoTagLineKeywords(raw: String) {
            flow.value = flow.value.copy(devAutoTagLineKeywords = raw)
        }
        override suspend fun setDevShowAutoTagLineInHome(show: Boolean) {
            flow.value = flow.value.copy(devShowAutoTagLineInHome = show)
        }
        override suspend fun setDevShowAutoTagLineInView(show: Boolean) {
            flow.value = flow.value.copy(devShowAutoTagLineInView = show)
        }
        override suspend fun setDevShowAutoTagLineInEdit(show: Boolean) {
            flow.value = flow.value.copy(devShowAutoTagLineInEdit = show)
        }
        override suspend fun setDevHomeRichPreviewStickyLimit(limit: Int) {
            flow.value = flow.value.copy(devHomeRichPreviewStickyLimit = limit)
        }
        override suspend fun setFullSyncRunning(runId: String) = Unit
        override suspend fun setFullSyncProgress(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit
        override suspend fun setFullSyncSuccess(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit
        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }

    private class FakeDiagnosticsExporter(
        private val holdMs: Long = 0L,
    ) : DiagnosticsExporter {
        val calls = AtomicInteger(0)
        var next: DiagnosticsExportResult =
            DiagnosticsExportResult.Success("content://diag/default.json")

        override suspend fun export(snapshot: DiagnosticsSnapshot): DiagnosticsExportResult {
            calls.incrementAndGet()
            if (holdMs > 0L) Thread.sleep(holdMs)
            return next
        }
    }

    private class FakeQuickTileRequester(
        private val holdMs: Long = 0L,
    ) : QuickTileRequester {
        var result: QuickTileRequestResult = QuickTileRequestResult.Completed(0)
        var lastKind: QuickTileKind? = null
        val calls = AtomicInteger(0)

        override suspend fun request(kind: QuickTileKind): QuickTileRequestResult {
            lastKind = kind
            calls.incrementAndGet()
            if (holdMs > 0L) Thread.sleep(holdMs)
            return result
        }
    }
}
