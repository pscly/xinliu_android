package cc.pscly.onememos.settings.appearance

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCommand
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsResult
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.quicktiles.OverlayPermissionGateway
import java.util.concurrent.atomic.AtomicBoolean
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

class AppearanceInteractionSettingsCapabilityImplTest {
    @Test
    fun observe_mapsFourAppearanceFields() =
        runBlocking {
            val repo =
                FakeSettingsRepository(
                    AppSettings(
                        themePalette = ThemePalette.CYBER,
                        themeMode = ThemeMode.DARK,
                        quickCaptureOverlayEnabled = true,
                        sealStampDurationMs = 800,
                    ),
                )
            val cap =
                AppearanceInteractionSettingsCapabilityImpl(
                    repo,
                    FakeOverlayGateway(granted = true),
                )
            val snap = cap.observe().first()
            assertEquals(ThemePalette.CYBER, snap.themePalette)
            assertEquals(ThemeMode.DARK, snap.themeMode)
            assertEquals(true, snap.quickCaptureOverlayEnabled)
            assertEquals(800, snap.sealStampDurationMs)
            assertEquals(null, snap.commandInFlight)
        }

    @Test
    fun execute_eachCommand_callsMatchingSetter() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val overlay = FakeOverlayGateway(granted = true)
            val cap = AppearanceInteractionSettingsCapabilityImpl(repo, overlay)

            assertEquals(
                AppearanceInteractionSettingsResult.Success,
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.INDIGO),
                ),
            )
            assertEquals(1, repo.paletteCalls.get())
            assertEquals(ThemePalette.INDIGO, repo.flow.value.themePalette)

            assertEquals(
                AppearanceInteractionSettingsResult.Success,
                cap.execute(AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.LIGHT)),
            )
            assertEquals(1, repo.modeCalls.get())
            assertEquals(ThemeMode.LIGHT, repo.flow.value.themeMode)

            assertEquals(
                AppearanceInteractionSettingsResult.Success,
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(true),
                ),
            )
            assertEquals(1, repo.overlayCalls.get())
            assertEquals(true, repo.flow.value.quickCaptureOverlayEnabled)

            assertEquals(
                AppearanceInteractionSettingsResult.Success,
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetSealStampDurationMs(1200),
                ),
            )
            assertEquals(1, repo.sealCalls.get())
            assertEquals(1200, repo.flow.value.sealStampDurationMs)
        }

    @Test
    fun enableOverlay_withoutPermission_returnsPlatformAction_andDoesNotPersist() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val overlay = FakeOverlayGateway(granted = false, packageName = "cc.pscly.onememos")
            val cap = AppearanceInteractionSettingsCapabilityImpl(repo, overlay)

            val result =
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(true),
                )
            val platform = result as AppearanceInteractionSettingsResult.Platform
            val action = platform.action as SettingsPlatformAction.OpenOverlayPermissionSettings
            assertEquals("cc.pscly.onememos", action.packageName)
            assertEquals(0, repo.overlayCalls.get())
            assertEquals(false, repo.flow.value.quickCaptureOverlayEnabled)
        }

    @Test
    fun enableOverlay_afterPermissionGranted_persists() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val overlay = FakeOverlayGateway(granted = false)
            val cap = AppearanceInteractionSettingsCapabilityImpl(repo, overlay)

            // 模拟用户从系统设置返回后已授权
            overlay.grantedFlag.set(true)
            val result =
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(true),
                )
            assertEquals(AppearanceInteractionSettingsResult.Success, result)
            assertEquals(true, repo.flow.value.quickCaptureOverlayEnabled)
        }

    @Test
    fun sealStampDuration_outOfRange_mapsInvalidInput() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val cap =
                AppearanceInteractionSettingsCapabilityImpl(
                    repo,
                    FakeOverlayGateway(granted = true),
                )
            assertEquals(
                AppearanceInteractionSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                cap.execute(AppearanceInteractionSettingsCommand.SetSealStampDurationMs(199)),
            )
            assertEquals(
                AppearanceInteractionSettingsResult.Failure(SettingsCapabilityError.InvalidInput),
                cap.execute(AppearanceInteractionSettingsCommand.SetSealStampDurationMs(2001)),
            )
            assertEquals(0, repo.sealCalls.get())
        }

    @Test
    fun concurrentSameCommand_secondIsIgnoredDuplicate() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings(), holdMs = 250L)
            val cap =
                AppearanceInteractionSettingsCapabilityImpl(
                    repo,
                    FakeOverlayGateway(granted = true),
                )
            val cmd = AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.DARK)
            val first = async(Dispatchers.IO) { cap.execute(cmd) }
            while (repo.modeCalls.get() == 0) {
                delay(5)
            }
            val second = withContext(Dispatchers.IO) { cap.execute(cmd) }
            assertEquals(AppearanceInteractionSettingsResult.IgnoredDuplicate, second)
            assertEquals(AppearanceInteractionSettingsResult.Success, first.await())
            assertEquals(1, repo.modeCalls.get())
        }

    @Test
    fun writeException_mapsToStorageFailure() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings(), failWrite = true)
            val cap =
                AppearanceInteractionSettingsCapabilityImpl(
                    repo,
                    FakeOverlayGateway(granted = true),
                )
            val result =
                cap.execute(
                    AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.CYBER),
                )
            assertTrue(result is AppearanceInteractionSettingsResult.Failure)
            assertEquals(
                SettingsCapabilityError.StorageFailure,
                (result as AppearanceInteractionSettingsResult.Failure).error,
            )
        }

    private class FakeOverlayGateway(
        granted: Boolean,
        override val packageName: String = "test.pkg",
    ) : OverlayPermissionGateway {
        val grantedFlag = AtomicBoolean(granted)

        override fun isGranted(): Boolean = grantedFlag.get()
    }

    private class FakeSettingsRepository(
        initial: AppSettings,
        private val holdMs: Long = 0L,
        private val failWrite: Boolean = false,
    ) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        val paletteCalls = AtomicInteger(0)
        val modeCalls = AtomicInteger(0)
        val overlayCalls = AtomicInteger(0)
        val sealCalls = AtomicInteger(0)

        override val settings: Flow<AppSettings> = flow

        private fun maybeHold() {
            if (holdMs > 0L) Thread.sleep(holdMs)
        }

        private fun maybeFail() {
            if (failWrite) throw IllegalStateException("datastore write failed")
        }

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun setToken(token: String) = Unit
        override suspend fun setLoginMode(mode: LoginMode) = Unit
        override suspend fun setCurrentUserCreator(creator: String) = Unit
        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit
        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit

        override suspend fun setThemePalette(palette: ThemePalette) {
            paletteCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(themePalette = palette)
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            modeCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(themeMode = mode)
        }

        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit

        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) {
            overlayCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(quickCaptureOverlayEnabled = enabled)
        }

        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) = Unit

        override suspend fun setSealStampDurationMs(durationMs: Int) {
            sealCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(sealStampDurationMs = durationMs)
        }

        override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) = Unit
        override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) = Unit
        override suspend fun setOfflineImagePrefetchMaxImages(count: Int) = Unit
        override suspend fun setAttachmentCacheMaxMb(mb: Int) = Unit
        override suspend fun setAttachmentUploadMaxMb(mb: Int) = Unit
        override suspend fun setTodoReminderMode(mode: TodoReminderMode) = Unit
        override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) = Unit
        override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) = Unit
        override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) = Unit
        override suspend fun setLastSyncSuccess() = Unit
        override suspend fun setLastSyncError(error: String, httpCode: Int) = Unit
        override suspend fun setDevAutoTagLineKeywords(raw: String) = Unit
        override suspend fun setDevShowAutoTagLineInHome(show: Boolean) = Unit
        override suspend fun setDevShowAutoTagLineInView(show: Boolean) = Unit
        override suspend fun setDevShowAutoTagLineInEdit(show: Boolean) = Unit
        override suspend fun setDevHomeRichPreviewStickyLimit(limit: Int) = Unit
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
        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit
        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }
}
