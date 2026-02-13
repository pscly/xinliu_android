package cc.pscly.onememos.collections

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.core.database.OneMemosDatabase
import cc.pscly.onememos.data.repository.CollectionsRepositoryImpl
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachment
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.domain.util.OwnerKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CollectionsRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var db: OneMemosDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, OneMemosDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createFolder_writesOutboxAndRequestsSync() =
        runBlocking {
            val scheduler = FakeTodoSyncScheduler()
            val repo =
                CollectionsRepositoryImpl(
                    collectionDao = db.collectionDao(),
                    todoSyncDao = db.todoSyncDao(),
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings(loginMode = LoginMode.BACKEND, token = "t"))),
                    todoSyncScheduler = scheduler,
                    ownerKeyProvider = FakeOwnerKeyProvider("o"),
                )

            val id = repo.createFolder(parentId = null, name = "做饭", color = "#fff")
            assertNotNull(db.collectionDao().getById(ownerKey = "o", id = id))

            val outbox = db.todoSyncDao().listPendingOutbox(ownerKey = "o", limit = 10)
            assertEquals(1, outbox.size)
            assertEquals("collection_item", outbox[0].resource)
            assertEquals("upsert", outbox[0].op)
            assertEquals(id, outbox[0].entityId)
            assertEquals(1, scheduler.requestSyncCalls)
        }

    @Test
    fun addMemoRef_withoutServerId_doesNotWriteOutbox() =
        runBlocking {
            val scheduler = FakeTodoSyncScheduler()
            val repo =
                CollectionsRepositoryImpl(
                    collectionDao = db.collectionDao(),
                    todoSyncDao = db.todoSyncDao(),
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings(loginMode = LoginMode.BACKEND, token = "t"))),
                    todoSyncScheduler = scheduler,
                    ownerKeyProvider = FakeOwnerKeyProvider("o"),
                )

            val memo = fakeMemo(uuid = "u1", serverId = null)
            val id = repo.addMemoRef(parentId = null, memo = memo, color = null, displayName = null)

            val saved = db.collectionDao().getById(ownerKey = "o", id = id)
            assertNotNull(saved)
            assertNull(saved!!.refId)
            assertEquals("u1", saved.refLocalUuid)

            assertEquals(0, db.todoSyncDao().listPendingOutbox(ownerKey = "o", limit = 10).size)
            assertEquals(0, scheduler.requestSyncCalls)
        }

    @Test
    fun backfillMemoRefId_updatesEntityAndWritesOutbox() =
        runBlocking {
            val scheduler = FakeTodoSyncScheduler()
            val repo =
                CollectionsRepositoryImpl(
                    collectionDao = db.collectionDao(),
                    todoSyncDao = db.todoSyncDao(),
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings(loginMode = LoginMode.BACKEND, token = "t"))),
                    todoSyncScheduler = scheduler,
                    ownerKeyProvider = FakeOwnerKeyProvider("o"),
                )

            val id = repo.addMemoRef(parentId = null, memo = fakeMemo(uuid = "u1", serverId = null), color = null, displayName = null)
            repo.backfillMemoRefId(memoUuid = "u1", memoServerId = "memos/123")

            val saved = db.collectionDao().getById(ownerKey = "o", id = id)
            assertEquals("memos/123", saved!!.refId)
            assertNull(saved.refLocalUuid)

            val outbox = db.todoSyncDao().listPendingOutbox(ownerKey = "o", limit = 10)
            assertEquals(1, outbox.size)
            assertEquals("collection_item", outbox[0].resource)
            assertEquals("upsert", outbox[0].op)
            assertEquals(id, outbox[0].entityId)
            assertEquals(1, scheduler.requestSyncCalls)
        }

    @Test
    fun deleteFolder_enqueuesDeleteForSubtree() =
        runBlocking {
            val scheduler = FakeTodoSyncScheduler()
            val repo =
                CollectionsRepositoryImpl(
                    collectionDao = db.collectionDao(),
                    todoSyncDao = db.todoSyncDao(),
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings(loginMode = LoginMode.BACKEND, token = "t"))),
                    todoSyncScheduler = scheduler,
                    ownerKeyProvider = FakeOwnerKeyProvider("o"),
                )

            val folderId = repo.createFolder(parentId = null, name = "root", color = null)
            val memoId = repo.addMemoRef(parentId = folderId, memo = fakeMemo(uuid = "u1", serverId = "memos/1"), color = null, displayName = null)

            db.todoSyncDao().deleteOutbox(ownerKey = "o", resource = "collection_item", entityId = folderId)
            db.todoSyncDao().deleteOutbox(ownerKey = "o", resource = "collection_item", entityId = memoId)
            scheduler.requestSyncCalls = 0

            repo.delete(folderId)

            val outbox = db.todoSyncDao().listPendingOutbox(ownerKey = "o", limit = 10)
            assertEquals(2, outbox.size)
            assertEquals(setOf(folderId, memoId), outbox.map { it.entityId }.toSet())
            assertEquals(setOf("delete"), outbox.map { it.op }.toSet())
            assertEquals(1, scheduler.requestSyncCalls)
        }

    private class FakeTodoSyncScheduler : TodoSyncScheduler {
        var requestSyncCalls: Int = 0

        override fun requestSync() {
            requestSyncCalls += 1
        }
    }

    private class FakeOwnerKeyProvider(
        private val ownerKey: String,
    ) : OwnerKeyProvider {
        override fun currentOwnerKeyOrNull(): String = ownerKey
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
        override suspend fun setThemePalette(palette: cc.pscly.onememos.domain.model.ThemePalette) = Unit
        override suspend fun setThemeMode(mode: cc.pscly.onememos.domain.model.ThemeMode) = Unit
        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit
        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit
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

    private fun fakeMemo(
        uuid: String,
        serverId: String?,
    ): Memo =
        Memo(
            uuid = uuid,
            serverId = serverId,
            creator = null,
            content = "",
            createdAt = 0L,
            updatedAt = 0L,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.SYNCED,
            attachments = emptyList<MemoAttachment>(),
            lastSyncError = null,
        )
}
