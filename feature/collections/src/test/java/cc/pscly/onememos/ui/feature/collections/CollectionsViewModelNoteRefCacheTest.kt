package cc.pscly.onememos.ui.feature.collections

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.CollectionRefType
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelNoteRefCacheTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun noteRef_targetMemoMissing_doesNotCrash_andIsNotInsertedIntoMap() =
        runBlocking {
            val childrenFlow = MutableStateFlow<List<CollectionItem>>(emptyList())
            val memoRepo = FakeMemoRepository(returnMemo = null)
            val vm =
                CollectionsViewModel(
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings(loginMode = LoginMode.BACKEND, token = "t"))),
                    collectionsRepository = FakeCollectionsRepository(childrenFlow),
                    memoRepository = memoRepo,
                )

            val collectJob = launch { vm.uiState.collect {} }
            try {
                val targetId = "memos/1"
                childrenFlow.value =
                    listOf(
                        CollectionItem(
                            id = "ref-1",
                            itemType = CollectionItemType.NOTE_REF,
                            parentId = null,
                            name = "ref",
                            color = null,
                            refType = CollectionRefType.MEMOS_MEMO,
                            refId = targetId,
                            sortOrder = 0,
                            clientUpdatedAtMs = 0L,
                            createdAt = "",
                            updatedAt = "",
                            deletedAt = null,
                            localOnly = false,
                            refLocalUuid = null,
                        ),
                    )

                await { memoRepo.getMemoCalls.get() == 1 }
                await { vm.uiState.value.items.size == 1 }

                assertTrue("缺失 memo 不应写入缓存 map", vm.uiState.value.memoByRefTargetId.isEmpty())
                assertFalse("缺失 memo 不应包含 key", vm.uiState.value.memoByRefTargetId.containsKey(targetId))
            } finally {
                collectJob.cancel()
            }
        }

    private suspend fun await(timeoutMs: Long = 1_500L, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private class FakeCollectionsRepository(
        private val childrenFlow: Flow<List<CollectionItem>>,
    ) : CollectionsRepository {
        override fun observeChildren(parentId: String?): Flow<List<CollectionItem>> = childrenFlow

        override fun observeAll(): Flow<List<CollectionItem>> = childrenFlow

        override suspend fun createFolder(parentId: String?, name: String, color: String?): String =
            throw NotImplementedError()

        override suspend fun addMemoRef(parentId: String?, memo: Memo, color: String?, displayName: String?): String =
            throw NotImplementedError()

        override suspend fun rename(id: String, name: String) = throw NotImplementedError()

        override suspend fun recolor(ids: List<String>, color: String?) = throw NotImplementedError()

        override suspend fun move(ids: List<String>, targetParentId: String?) = throw NotImplementedError()

        override suspend fun reorder(parentId: String?, orderedIds: List<String>) = throw NotImplementedError()

        override suspend fun delete(id: String) = throw NotImplementedError()

        override suspend fun batchDelete(ids: List<String>) = throw NotImplementedError()

        override suspend fun backfillMemoRefId(memoUuid: String, memoServerId: String) = throw NotImplementedError()
    }

    private class FakeMemoRepository(
        private val returnMemo: Memo?,
    ) : MemoRepository {
        val getMemoCalls = AtomicInteger(0)

        override fun observeMemos(): Flow<List<Memo>> = emptyFlow()

        override fun observeArchivedMemos(): Flow<List<Memo>> = emptyFlow()

        override fun observeAllMemos(): Flow<List<Memo>> = emptyFlow()

        override fun observeRecentMemos(limit: Int): Flow<List<Memo>> = emptyFlow()

        override fun observeMemosByCreatedAtRange(startInclusive: Long, endExclusive: Long): Flow<List<Memo>> = emptyFlow()

        override suspend fun listRecentEditedActiveMemos(limit: Int): List<Memo> = emptyList()

        override suspend fun getMemo(uuid: String): Memo? {
            getMemoCalls.incrementAndGet()
            return returnMemo
        }

        override suspend fun archiveMemo(uuid: String) = Unit

        override suspend fun unarchiveMemo(uuid: String) = Unit

        override suspend fun setPinned(uuid: String, pinned: Boolean) = Unit

        override suspend fun updateMemoContent(uuid: String, content: String) = Unit

        override suspend fun createLocalMemo(content: String, resourceUris: List<String>): String =
            throw NotImplementedError()

        override suspend fun updateLocalMemo(uuid: String, content: String, resourceUris: List<String>) = Unit

        override suspend fun updateMemoDraft(uuid: String, content: String, attachments: List<MemoAttachmentDraft>) = Unit
    }

    private class FakeSettingsRepository(
        override val settings: Flow<AppSettings>,
    ) : SettingsRepository {
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
        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
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
        override suspend fun setFullSyncProgress(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int) = Unit
        override suspend fun setFullSyncSuccess(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int) = Unit
        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit
        override suspend fun setFullSyncFailed(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int, error: String) = Unit
    }

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
