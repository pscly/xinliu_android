package cc.pscly.onememos.collections

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import cc.pscly.onememos.core.database.OneMemosDatabase
import cc.pscly.onememos.core.database.entity.CollectionItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CollectionDaoTest {
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
    fun listChildren_sortedBySortOrderThenClientUpdatedAtThenId() =
        runBlocking {
            val dao = db.collectionDao()
            val ownerKey = "o"

            dao.upsertAll(
                listOf(
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "b",
                        itemType = "folder",
                        parentId = null,
                        name = "b",
                        sortOrder = 1,
                        clientUpdatedAtMs = 10,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "a",
                        itemType = "folder",
                        parentId = null,
                        name = "a",
                        sortOrder = 1,
                        clientUpdatedAtMs = 20,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "c",
                        itemType = "folder",
                        parentId = null,
                        name = "c",
                        sortOrder = 0,
                        clientUpdatedAtMs = 5,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "d",
                        itemType = "folder",
                        parentId = null,
                        name = "d",
                        sortOrder = 1,
                        clientUpdatedAtMs = 20,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                ),
            )

            val ids = dao.listChildren(ownerKey = ownerKey, parentId = null).map { it.id }
            assertEquals(listOf("c", "a", "d", "b"), ids)
        }

    @Test
    fun tombstoneSubtree_marksAllDescendants() =
        runBlocking {
            val dao = db.collectionDao()
            val ownerKey = "o"

            val rootId = "root"
            val childFolderId = "child"
            val leafNoteId = "leaf"

            dao.upsertAll(
                listOf(
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = rootId,
                        itemType = "folder",
                        parentId = null,
                        name = "root",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = childFolderId,
                        itemType = "folder",
                        parentId = rootId,
                        name = "child",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = leafNoteId,
                        itemType = "note_ref",
                        parentId = childFolderId,
                        name = "",
                        refType = "memos_memo",
                        refId = "memos/1",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                ),
            )

            val subtreeIds = dao.listSubtree(ownerKey = ownerKey, rootId = rootId).map { it.id }.sorted()
            assertEquals(listOf(childFolderId, leafNoteId, rootId).sorted(), subtreeIds)

            val deletedAt = "2026-02-13T01:02:03Z"
            val updatedAt = "2026-02-13T01:02:03Z"
            dao.tombstoneSubtree(
                ownerKey = ownerKey,
                rootId = rootId,
                deletedAt = deletedAt,
                clientUpdatedAtMs = 99,
                updatedAt = updatedAt,
            )

            val all = dao.listAll(ownerKey = ownerKey, includeDeleted = true).associateBy { it.id }
            assertEquals(deletedAt, all[rootId]!!.deletedAt)
            assertEquals(deletedAt, all[childFolderId]!!.deletedAt)
            assertEquals(deletedAt, all[leafNoteId]!!.deletedAt)

            val visibleRootChildren = dao.listChildren(ownerKey = ownerKey, parentId = null, includeDeleted = false)
            assertEquals(emptyList<CollectionItemEntity>(), visibleRootChildren)
        }

    @Test
    fun listChildren_includeDeleted_false_filtersTombstone() =
        runBlocking {
            val dao = db.collectionDao()
            val ownerKey = "o"

            dao.upsert(
                CollectionItemEntity(
                    ownerKey = ownerKey,
                    id = "x",
                    itemType = "folder",
                    parentId = null,
                    name = "x",
                    sortOrder = 0,
                    clientUpdatedAtMs = 1,
                    createdAt = "2026-02-13T00:00:00Z",
                    updatedAt = "2026-02-13T00:00:00Z",
                    deletedAt = "2026-02-13T00:00:01Z",
                ),
            )

            assertEquals(emptyList<CollectionItemEntity>(), dao.listChildren(ownerKey = ownerKey, parentId = null, includeDeleted = false))
            assertEquals(1, dao.listChildren(ownerKey = ownerKey, parentId = null, includeDeleted = true).size)
        }

    @Test
    fun listMemoRefBackfillTargets_filtersNullRefIdAndLocalUuidAndNotLocalOnly() =
        runBlocking {
            val dao = db.collectionDao()
            val ownerKey = "o"

            dao.upsertAll(
                listOf(
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "t1",
                        itemType = "note_ref",
                        parentId = null,
                        name = "",
                        refType = "memos_memo",
                        refId = null,
                        refLocalUuid = "u1",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "t2",
                        itemType = "note_ref",
                        parentId = null,
                        name = "",
                        refType = "memos_memo",
                        refId = "memos/1",
                        refLocalUuid = "u1",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "t3",
                        itemType = "note_ref",
                        parentId = null,
                        name = "",
                        refType = "memos_memo",
                        refId = null,
                        refLocalUuid = "u1",
                        localOnly = true,
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                    CollectionItemEntity(
                        ownerKey = ownerKey,
                        id = "t4",
                        itemType = "note_ref",
                        parentId = null,
                        name = "",
                        refType = "memos_memo",
                        refId = null,
                        refLocalUuid = "u2",
                        sortOrder = 0,
                        clientUpdatedAtMs = 1,
                        createdAt = "2026-02-13T00:00:00Z",
                        updatedAt = "2026-02-13T00:00:00Z",
                    ),
                ),
            )

            val hits = dao.listMemoRefBackfillTargets(ownerKey = ownerKey, refType = "memos_memo", memoUuid = "u1")
            assertEquals(listOf("t1"), hits.map { it.id })
        }
}
