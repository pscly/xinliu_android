package cc.pscly.onememos.settings.record

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsResult
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
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

class RecordEditingSettingsCapabilityImplTest {
    @Test
    fun observe_mapsFiveRecordEditingFields() =
        runBlocking {
            val repo =
                FakeSettingsRepository(
                    AppSettings(
                        defaultVisibility = MemoVisibility.PUBLIC,
                        regexSearchEnabled = true,
                        showTagCountsInFilter = false,
                        quickInsertTimeEnabled = true,
                        quickInsertTimeFormat = QuickInsertTimeFormat.TIME_ONLY,
                    ),
                )
            val cap = RecordEditingSettingsCapabilityImpl(repo)
            val snap = cap.observe().first()
            assertEquals(MemoVisibility.PUBLIC, snap.defaultVisibility)
            assertEquals(true, snap.regexSearchEnabled)
            assertEquals(false, snap.showTagCounts)
            assertEquals(true, snap.quickInsertTimeEnabled)
            assertEquals(QuickInsertTimeFormat.TIME_ONLY, snap.quickInsertTimeFormat)
            assertEquals(null, snap.commandInFlight)
        }

    @Test
    fun execute_eachCommand_callsMatchingSetterOnce() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings())
            val cap = RecordEditingSettingsCapabilityImpl(repo)

            assertEquals(
                RecordEditingSettingsResult.Success,
                cap.execute(RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PROTECTED)),
            )
            assertEquals(1, repo.visibilityCalls.get())
            assertEquals(MemoVisibility.PROTECTED, repo.flow.value.defaultVisibility)

            assertEquals(
                RecordEditingSettingsResult.Success,
                cap.execute(RecordEditingSettingsCommand.SetRegexSearchEnabled(true)),
            )
            assertEquals(1, repo.regexCalls.get())
            assertEquals(true, repo.flow.value.regexSearchEnabled)

            assertEquals(
                RecordEditingSettingsResult.Success,
                cap.execute(RecordEditingSettingsCommand.SetShowTagCounts(false)),
            )
            assertEquals(1, repo.tagCountCalls.get())
            assertEquals(false, repo.flow.value.showTagCountsInFilter)

            assertEquals(
                RecordEditingSettingsResult.Success,
                cap.execute(RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(true)),
            )
            assertEquals(1, repo.quickInsertEnabledCalls.get())
            assertEquals(true, repo.flow.value.quickInsertTimeEnabled)

            assertEquals(
                RecordEditingSettingsResult.Success,
                cap.execute(RecordEditingSettingsCommand.SetQuickInsertTimeFormat(QuickInsertTimeFormat.FULL_DATETIME)),
            )
            assertEquals(1, repo.quickInsertFormatCalls.get())
            assertEquals(QuickInsertTimeFormat.FULL_DATETIME, repo.flow.value.quickInsertTimeFormat)
        }

    @Test
    fun concurrentSameCommand_secondIsIgnoredDuplicate() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings(), holdMs = 250L)
            val cap = RecordEditingSettingsCapabilityImpl(repo)
            val cmd = RecordEditingSettingsCommand.SetRegexSearchEnabled(true)
            val first = async(Dispatchers.IO) { cap.execute(cmd) }
            while (repo.regexCalls.get() == 0) {
                delay(5)
            }
            val second = withContext(Dispatchers.IO) { cap.execute(cmd) }
            assertEquals(RecordEditingSettingsResult.IgnoredDuplicate, second)
            assertEquals(RecordEditingSettingsResult.Success, first.await())
            assertEquals(1, repo.regexCalls.get())
        }

    @Test
    fun writeException_mapsToStorageFailure() =
        runBlocking {
            val repo = FakeSettingsRepository(AppSettings(), failWrite = true)
            val cap = RecordEditingSettingsCapabilityImpl(repo)
            val result = cap.execute(RecordEditingSettingsCommand.SetShowTagCounts(true))
            assertTrue(result is RecordEditingSettingsResult.Failure)
            assertEquals(
                SettingsCapabilityError.StorageFailure,
                (result as RecordEditingSettingsResult.Failure).error,
            )
        }

    private class FakeSettingsRepository(
        initial: AppSettings,
        private val holdMs: Long = 0L,
        private val failWrite: Boolean = false,
    ) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        val visibilityCalls = AtomicInteger(0)
        val regexCalls = AtomicInteger(0)
        val tagCountCalls = AtomicInteger(0)
        val quickInsertEnabledCalls = AtomicInteger(0)
        val quickInsertFormatCalls = AtomicInteger(0)

        override val settings: Flow<AppSettings> = flow

        private fun maybeHold() {
            if (holdMs > 0L) {
                Thread.sleep(holdMs)
            }
        }

        private fun maybeFail() {
            if (failWrite) {
                throw IllegalStateException("datastore write failed")
            }
        }

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit

        override suspend fun setServerUrl(url: String) = Unit

        override suspend fun setToken(token: String) = Unit

        override suspend fun setLoginMode(mode: LoginMode) = Unit

        override suspend fun setCurrentUserCreator(creator: String) = Unit

        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit

        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit

        override suspend fun setThemePalette(palette: ThemePalette) = Unit
        override suspend fun setThemeDescriptor(descriptor: ThemeDescriptor) = Unit

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setDefaultVisibility(visibility: MemoVisibility) {
            visibilityCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(defaultVisibility = visibility)
        }

        override suspend fun setRegexSearchEnabled(enabled: Boolean) {
            regexCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(regexSearchEnabled = enabled)
        }

        override suspend fun setShowTagCountsInFilter(enabled: Boolean) {
            tagCountCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(showTagCountsInFilter = enabled)
        }

        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit

        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) {
            quickInsertEnabledCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(quickInsertTimeEnabled = enabled)
        }

        override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) {
            quickInsertFormatCalls.incrementAndGet()
            maybeHold()
            maybeFail()
            flow.value = flow.value.copy(quickInsertTimeFormat = format)
        }

        override suspend fun setSealStampDurationMs(durationMs: Int) = Unit

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
