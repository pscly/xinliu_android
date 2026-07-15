package cc.pscly.onememos.ui.feature.quickcapture

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraft
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QuickCaptureDraftViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        // ViewModel 在单测里不会自动触发 onCleared()，可能遗留 DraftAutoSaver 的 1s 防抖任务；
        // 等待其自然落盘后再清空草稿，避免写入“串”到后续用例导致不稳定。
        runBlocking {
            delay(1_100L)
            runCatching { QuickCaptureDraftStore(context).clearDraft() }
        }
    }

    @Test
    fun init_draftExists_bannerVisible() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "draft", attachments = emptyList()))

            val vm = newViewModel(memoRepo = FakeMemoRepository(), settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))

            assertTrue(vm.uiState.value.draftBannerVisible)
            assertFalse(vm.uiState.value.draftOverwriteDialogVisible)
        }

    @Test
    fun restoreDraft_setsContentAndHidesBanner() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "hello", attachments = emptyList()))

            val vm = newViewModel(memoRepo = FakeMemoRepository(), settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))
            assertTrue(vm.uiState.value.draftBannerVisible)

            vm.restoreDraft()
            await { vm.uiState.value.content.text == "hello" }
            assertFalse(vm.uiState.value.draftBannerVisible)
        }

    @Test
    fun clearDraft_hidesBannerAndDeletesStoredDraft() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "x", attachments = emptyList()))

            val vm = newViewModel(memoRepo = FakeMemoRepository(), settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))
            assertTrue(vm.uiState.value.draftBannerVisible)

            vm.clearDraft()
            await { !vm.uiState.value.draftBannerVisible }
            await { store.loadDraft() == null }
        }

    @Test
    fun overwriteConfirm_typingWhenDraftExists_showsDialogAndDoesNotChangeContentUntilConfirmed() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "old", attachments = emptyList()))

            val vm = newViewModel(memoRepo = FakeMemoRepository(), settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))
            assertEquals("", vm.uiState.value.content.text)
            assertTrue(vm.uiState.value.draftBannerVisible)

            vm.updateContent(TextFieldValue("new"))
            assertTrue(vm.uiState.value.draftOverwriteDialogVisible)
            assertEquals("", vm.uiState.value.content.text)
        }

    @Test
    fun confirmOverwrite_appliesPendingContentAndOverwritesStoredDraftAfterFlush() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "old", attachments = emptyList()))

            val vm = newViewModel(memoRepo = FakeMemoRepository(), settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))

            vm.updateContent(TextFieldValue("new"))
            assertTrue(vm.uiState.value.draftOverwriteDialogVisible)

            vm.confirmOverwriteAndApplyPending()
            await { vm.uiState.value.content.text == "new" }
            await { store.loadDraft()?.text == "new" }
        }

    @Test
    fun save_successForNewMemo_clearsDraft_failureKeepsDraft() =
        runBlocking {
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            run {
                val repo = FakeMemoRepository().apply { shouldCreateFail = false }
                val vm = newViewModel(memoRepo = repo, settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))

                vm.updateContent(TextFieldValue("memo"))
                vm.flushDraftNow()
                await { store.loadDraft()?.text == "memo" }

                vm.save()
                await { repo.createLocalMemoCalls == 1 }
                await { store.loadDraft() == null }
            }

            run {
                val repo = FakeMemoRepository().apply { shouldCreateFail = true }
                val vm = newViewModel(memoRepo = repo, settingsRepo = FakeSettingsRepository(flowOf(AppSettings())))

                vm.updateContent(TextFieldValue("memo2"))
                vm.flushDraftNow()
                await { store.loadDraft()?.text == "memo2" }

                vm.save()
                await { repo.createLocalMemoCalls == 1 }
                await { store.loadDraft()?.text == "memo2" }
            }
        }

    private fun newViewModel(
        memoRepo: MemoRepository,
        settingsRepo: SettingsRepository,
    ): QuickCaptureViewModel =
        QuickCaptureViewModel(
            memoRepository = memoRepo,
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
        override suspend fun setQuickInsertTimeFormat(format: cc.pscly.onememos.domain.model.QuickInsertTimeFormat) = Unit
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

        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit

        override suspend fun setFullSyncFailed(
            runId: String,
            stage: cc.pscly.onememos.domain.model.FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }

    private class FakeMemoRepository : MemoRepository {
        var shouldCreateFail: Boolean = false
        var createLocalMemoCalls: Int = 0

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

        override suspend fun createLocalMemo(content: String, resourceUris: List<String>): String {
            createLocalMemoCalls += 1
            if (shouldCreateFail) throw IllegalStateException("boom")
            return "new_uuid"
        }

        override suspend fun updateLocalMemo(uuid: String, content: String, resourceUris: List<String>) = Unit
        override suspend fun updateMemoDraft(uuid: String, content: String, attachments: List<MemoAttachmentDraft>) = Unit
    }
}
