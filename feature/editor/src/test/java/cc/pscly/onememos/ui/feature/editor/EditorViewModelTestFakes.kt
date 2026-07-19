package cc.pscly.onememos.ui.feature.editor

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import cc.pscly.onememos.domain.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

internal class FakeMemoRepository(
    private val memo: Memo,
) : MemoRepository {
    override fun observeMemos(): Flow<List<Memo>> = flowOf(emptyList())

    override fun observeArchivedMemos(): Flow<List<Memo>> = flowOf(emptyList())

    override fun observeAllMemos(): Flow<List<Memo>> = flowOf(emptyList())

    override fun observeRecentMemos(limit: Int): Flow<List<Memo>> = flowOf(emptyList())

    override fun observeMemosByCreatedAtRange(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Memo>> = flowOf(emptyList())

    override suspend fun listRecentEditedActiveMemos(limit: Int): List<Memo> = emptyList()

    override suspend fun getMemo(uuid: String): Memo? =
        if (uuid == memo.uuid) memo else null

    override suspend fun archiveMemo(uuid: String) = Unit

    override suspend fun unarchiveMemo(uuid: String) = Unit

    override suspend fun setPinned(
        uuid: String,
        pinned: Boolean,
    ) = Unit

    override suspend fun updateMemoContent(
        uuid: String,
        content: String,
    ) = Unit

    override suspend fun createLocalMemo(
        content: String,
        resourceUris: List<String>,
    ): String = throw NotImplementedError()

    override suspend fun updateLocalMemo(
        uuid: String,
        content: String,
        resourceUris: List<String>,
    ) = Unit

    override suspend fun updateMemoDraft(
        uuid: String,
        content: String,
        attachments: List<MemoAttachmentDraft>,
    ) = Unit
}

internal class FakeSettingsRepository(
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

    override suspend fun setQuickInsertTimeFormat(
        format: QuickInsertTimeFormat,
    ) = Unit

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

    override suspend fun setLastSyncError(
        error: String,
        httpCode: Int,
    ) = Unit

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

internal class FakeCacheRepository : CacheRepository {
    override suspend fun getCacheStats(): CacheStats =
        CacheStats(
            databaseBytes = 0L,
            imageCacheBytes = 0L,
            attachmentCacheBytes = 0L,
            otherCacheBytes = 0L,
        )

    override suspend fun clearImageCache() = Unit

    override suspend fun clearAttachmentCache() = Unit

    override suspend fun clearAllCache() = Unit

    override suspend fun ensureImageAttachmentCached(
        serverBase: String,
        memoUuid: String,
        remoteName: String,
        filename: String,
    ): String? = null
}

internal class FakeSyncScheduler : SyncScheduler {
    override fun requestSync() = Unit

    override suspend fun requestFullResync(): FullResyncScheduleResult =
        FullResyncScheduleResult.Accepted(requestId = "test")
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
