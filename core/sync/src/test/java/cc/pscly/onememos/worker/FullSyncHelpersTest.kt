package cc.pscly.onememos.worker

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.core.database.OneMemosDatabase
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import cc.pscly.onememos.domain.derived.MemoDerivedFieldsDeriver
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FullSyncHelpersTest {
    private lateinit var context: Context
    private lateinit var db: OneMemosDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(context, OneMemosDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullSync_paginatesUntilNextPageTokenBlank_forNormalAndArchived() =
        runBlocking {
            val api =
                FakeMemosApi(
                    responses =
                        mapOf(
                            // NORMAL: 2 pages
                            Key(state = MemoServerState.NORMAL.name, pageToken = null) to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/1", content = "n1")),
                                    nextPageToken = "n2",
                                ),
                            Key(state = MemoServerState.NORMAL.name, pageToken = "n2") to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/2", content = "n2")),
                                    nextPageToken = "",
                                ),
                            // ARCHIVED: 2 pages
                            Key(state = MemoServerState.ARCHIVED.name, pageToken = null) to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/3", content = "a1")),
                                    nextPageToken = "a2",
                                ),
                            Key(state = MemoServerState.ARCHIVED.name, pageToken = "a2") to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/4", content = "a2")),
                                    nextPageToken = null,
                                ),
                        ),
                )

            val progressStages = mutableListOf<FullSyncStage>()
            val progressPages = mutableListOf<Int>()
            val progressItems = mutableListOf<Int>()

            val tracker = FullSyncTracker()
            refreshFromServerFull(
                serverBase = "https://example.com/",
                runId = "run-1",
                tracker = tracker,
                memoDao = db.memoDao(),
                memosApi = api,
                creatorState = CreatorState(currentCreator = ""),
                ensureActive = {},
                onCreatorResolved = {},
                onProgress = { _, stage, pages, items ->
                    progressStages += stage
                    progressPages += pages
                    progressItems += items
                },
            )

            // NORMAL 两页 + ARCHIVED 两页
            assertEquals(4, api.calls.size)
            assertEquals(MemoServerState.NORMAL.name, api.calls[0].state)
            assertEquals(null, api.calls[0].pageToken)
            assertEquals(MemoServerState.NORMAL.name, api.calls[1].state)
            assertEquals("n2", api.calls[1].pageToken)
            assertEquals(MemoServerState.ARCHIVED.name, api.calls[2].state)
            assertEquals(null, api.calls[2].pageToken)
            assertEquals(MemoServerState.ARCHIVED.name, api.calls[3].state)
            assertEquals("a2", api.calls[3].pageToken)

            // 进度写回应按页累计
            assertEquals(listOf(1, 2, 3, 4), progressPages)
            assertEquals(listOf(1, 2, 3, 4), progressItems)
            assertEquals(
                listOf(
                    FullSyncStage.NORMAL,
                    FullSyncStage.NORMAL,
                    FullSyncStage.ARCHIVED,
                    FullSyncStage.ARCHIVED,
                ),
                progressStages,
            )

            assertEquals(FullSyncStage.ARCHIVED, tracker.stage)
            assertEquals(4, tracker.pagesFetchedTotal)
            assertEquals(4, tracker.itemsFetchedTotal)
        }

    @Test
    fun fullSync_throws_whenNextPageTokenRepeats_toAvoidInfiniteLoop() =
        runBlocking {
            val api =
                FakeMemosApi(
                    responses =
                        mapOf(
                            // NORMAL：第一页返回 token=dup，第二页再次返回 token=dup（触发重复 token 防护）。
                            Key(state = MemoServerState.NORMAL.name, pageToken = null) to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/1", content = "n1")),
                                    nextPageToken = "dup",
                                ),
                            Key(state = MemoServerState.NORMAL.name, pageToken = "dup") to
                                ListMemosResponseDto(
                                    memos = listOf(MemoDto(name = "memos/2", content = "n2")),
                                    nextPageToken = "dup",
                                ),
                        ),
                )

            val progressPages = mutableListOf<Int>()
            val progressItems = mutableListOf<Int>()
            val tracker = FullSyncTracker()

            try {
                refreshFromServerFull(
                    serverBase = "https://example.com/",
                    runId = "run-dup-token",
                    tracker = tracker,
                    memoDao = db.memoDao(),
                    memosApi = api,
                    creatorState = CreatorState(currentCreator = ""),
                    ensureActive = {},
                    onCreatorResolved = {},
                    onProgress = { _, _, pages, items ->
                        progressPages += pages
                        progressItems += items
                    },
                )
                org.junit.Assert.fail("预期重复分页 token 会抛出异常，以避免死循环")
            } catch (_: IllegalStateException) {
                // ok
            }

            // guard 在发起第三次请求前触发：仅会请求到 null / dup 两个 token。
            assertEquals(2, api.calls.size)
            assertEquals(MemoServerState.NORMAL.name, api.calls[0].state)
            assertEquals(null, api.calls[0].pageToken)
            assertEquals(MemoServerState.NORMAL.name, api.calls[1].state)
            assertEquals("dup", api.calls[1].pageToken)

            assertEquals(listOf(1, 2), progressPages)
            assertEquals(listOf(1, 2), progressItems)
            assertEquals(FullSyncStage.NORMAL, tracker.stage)
            assertEquals(2, tracker.pagesFetchedTotal)
            assertEquals(2, tracker.itemsFetchedTotal)
        }

    @Test
    fun fullSync_doesNotCallSuccess_whenCancelled() =
        runBlocking {
            val api = FakeMemosApi(responses = emptyMap())

            var successCalls = 0
            val tracker = FullSyncTracker()

            try {
                performFullSync(
                    serverBase = "https://example.com/",
                    runId = "run-1",
                    tracker = tracker,
                    memoDao = db.memoDao(),
                    memosApi = api,
                    initialCreator = "",
                    ensureActive = { throw CancellationException("cancel") },
                    onCreatorResolved = {},
                    onProgress = { _, _, _, _ -> },
                    onSuccess = { _, _, _, _ -> successCalls += 1 },
                )
            } catch (_: CancellationException) {
                // ok
            }

            assertEquals(0, successCalls)
        }

    @Test
    fun upsertRemoteMemoFromDtoIfSafe_doesNotOverwriteLocalWhenNotSynced() =
        runBlocking {
            val dao = db.memoDao()

            val serverId = "memos/1"
            val derived = MemoDerivedFieldsDeriver.derive(content = "local-content", now = 123L)
            val local =
                MemoEntity(
                    localId = 0,
                    uuid = serverId,
                    serverId = serverId,
                    creator = "users/1",
                    content = "local-content",
                    plainPreview = derived.plainPreview,
                    tagsText = derived.tagsText,
                    derivedVersion = derived.derivedVersion,
                    derivedAt = derived.derivedAt,
                    createdAt = 1L,
                    updatedAt = 2L,
                    serverState = MemoServerState.NORMAL,
                    visibility = MemoVisibility.PRIVATE,
                    pinned = false,
                    syncStatus = SyncStatus.DIRTY,
                    lastSyncError = "",
                )
            dao.upsertMemoWithAttachments(local, emptyList())

            val remote = MemoDto(name = serverId, content = "remote-content")
            upsertRemoteMemoFromDtoIfSafe(
                dto = remote,
                memoDao = dao,
                creatorState = CreatorState(currentCreator = "users/1"),
                onCreatorResolved = {},
            )

            val after = dao.getMemoByServerId(serverId)!!.memo
            assertEquals("local-content", after.content)
            assertEquals(SyncStatus.DIRTY, after.syncStatus)
        }

    private data class Key(
        val state: String?,
        val pageToken: String?,
    )

    private data class ListMemosCall(
        val pageSize: Int,
        val pageToken: String?,
        val state: String?,
        val orderBy: String?,
    )

    private class FakeMemosApi(
        private val responses: Map<Key, ListMemosResponseDto>,
    ) : MemosApi {
        val calls = mutableListOf<ListMemosCall>()

        override suspend fun listMemos(
            url: String,
            pageSize: Int,
            pageToken: String?,
            state: String?,
            orderBy: String?,
            filter: String?,
            showDeleted: Boolean?,
        ): ListMemosResponseDto {
            calls += ListMemosCall(pageSize = pageSize, pageToken = pageToken, state = state, orderBy = orderBy)
            return responses[Key(state = state, pageToken = pageToken)]
                ?: ListMemosResponseDto(memos = emptyList(), nextPageToken = "")
        }

        override suspend fun authStatus(url: String): JsonObject =
            throw UnsupportedOperationException("not needed")

        override suspend fun currentUser(url: String): JsonObject =
            throw UnsupportedOperationException("not needed")

        override suspend fun getMemo(url: String): MemoDto =
            throw UnsupportedOperationException("not needed")

        override suspend fun createMemo(url: String, memo: CreateMemoRequestDto): MemoDto =
            throw UnsupportedOperationException("not needed")

        override suspend fun updateMemo(url: String, updateMask: String, memo: UpdateMemoRequestDto): MemoDto =
            throw UnsupportedOperationException("not needed")

        override suspend fun setMemoAttachments(url: String, body: SetMemoAttachmentsRequestDto): EmptyDto =
            throw UnsupportedOperationException("not needed")

        override suspend fun createAttachment(url: String, attachment: CreateAttachmentRequestDto): AttachmentDto =
            throw UnsupportedOperationException("not needed")

        override suspend fun createAttachmentRaw(url: String, body: RequestBody): AttachmentDto =
            throw UnsupportedOperationException("not needed")
    }
}
