package cc.pscly.onememos.ui.feature.quickcapture

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QuickCaptureInsertTimeFormatTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        runBlocking {
            delay(1_100L)
            runCatching { QuickCaptureDraftStore(context).clearDraft() }
        }
    }

    @Test
    fun insertCurrentTimeStamp_fullDateTimeFormat_insertsQuotedFullTimestamp() =
        runBlocking {
            val vm =
                newViewModel(
                    FakeSettingsRepository(
                        flowOf(
                            AppSettings(
                                quickInsertTimeEnabled = true,
                                quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
                            ),
                        ),
                    ),
                )

            vm.updateContent(TextFieldValue(""))
            vm.insertCurrentTimeStamp()

            await { vm.uiState.value.content.text.isNotBlank() }
            assertTrue(vm.uiState.value.content.text.matches(Regex("> \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\n\\n")))
        }

    @Test
    fun insertCurrentTimeStamp_timeOnlyFormat_insertsQuotedTimeOnlyTimestamp() =
        runBlocking {
            val vm =
                newViewModel(
                    FakeSettingsRepository(
                        flowOf(
                            AppSettings(
                                quickInsertTimeEnabled = true,
                                quickInsertTimeFormat = QuickInsertTimeFormat.TIME_ONLY,
                            ),
                        ),
                    ),
                )

            vm.updateContent(TextFieldValue(""))
            vm.insertCurrentTimeStamp()

            await { vm.uiState.value.content.text.isNotBlank() }
            assertTrue(vm.uiState.value.content.text.matches(Regex("> \\d{2}:\\d{2}:\\d{2}\\n\\n")))
        }

    private fun newViewModel(settingsRepo: SettingsRepository): QuickCaptureViewModel =
        QuickCaptureViewModel(
            memoRepository = FakeMemoRepository(),
            settingsRepository = settingsRepo,
            appContext = context,
        )

    private suspend fun await(timeoutMs: Long = 1_500L, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private class FakeSettingsRepository(
        override val settings: Flow<AppSettings>,
    ) : SettingsRepository {
        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun setToken(token: String) = Unit
        override suspend fun setLoginMode(mode: cc.pscly.onememos.domain.model.LoginMode) = Unit
        override suspend fun setCurrentUserCreator(creator: String) = Unit
        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit
        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit
        override suspend fun setThemePalette(palette: cc.pscly.onememos.domain.model.ThemePalette) = Unit
        override suspend fun setThemeMode(mode: cc.pscly.onememos.domain.model.ThemeMode) = Unit
        override suspend fun setDefaultVisibility(visibility: cc.pscly.onememos.domain.model.MemoVisibility) = Unit
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
        override suspend fun setAttachmentUploadMaxMb(mb: Int) = Unit
        override suspend fun setTodoReminderMode(mode: cc.pscly.onememos.domain.model.TodoReminderMode) = Unit
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
            stage: cc.pscly.onememos.domain.model.FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit

        override suspend fun setFullSyncSuccess(
            runId: String,
            stage: cc.pscly.onememos.domain.model.FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit

        override suspend fun setFullSyncFailed(
            runId: String,
            stage: cc.pscly.onememos.domain.model.FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }

    private class FakeMemoRepository : MemoRepository {
        override fun observeMemos(): Flow<List<Memo>> = flowOf(emptyList())
        override fun observeArchivedMemos(): Flow<List<Memo>> = flowOf(emptyList())
        override fun observeAllMemos(): Flow<List<Memo>> = flowOf(emptyList())
        override fun observeRecentMemos(limit: Int): Flow<List<Memo>> = flowOf(emptyList())
        override fun observeMemosByCreatedAtRange(startInclusive: Long, endExclusive: Long): Flow<List<Memo>> = flowOf(emptyList())
override suspend fun listRecentEditedActiveMemos(limit: Int): List<Memo> = emptyList()
        override suspend fun getMemo(uuid: String): Memo? = null
        override suspend fun archiveMemo(uuid: String) = Unit
        override suspend fun unarchiveMemo(uuid: String) = Unit
        override suspend fun updateMemoContent(uuid: String, content: String) = Unit
        override suspend fun createLocalMemo(content: String, resourceUris: List<String>): String = "uuid"
        override suspend fun updateLocalMemo(uuid: String, content: String, resourceUris: List<String>) = Unit
        override suspend fun updateMemoDraft(uuid: String, content: String, attachments: List<MemoAttachmentDraft>) = Unit
    }
}
