package cc.pscly.onememos.domain.util

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoOccurrence
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoRelationToggleTest {

    private fun sampleMemo(uuid: String, content: String): Memo =
        Memo(
            uuid = uuid,
            serverId = null,
            creator = null,
            content = content,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.SYNCED,
            attachments = emptyList(),
            lastSyncError = null,
        )

    @Test
    fun toggleTodo_add_then_remove_is_strong_cancel() =
        runBlocking {
            val memos = FakeMemos()
            val todos = FakeTodos()
            val cols = FakeCollections()
            memos.put(sampleMemo("m1", "买牛奶\n第二行"))
            val toggle =
                MemoRelationToggle(
                    memoRepository = memos,
                    todoRepository = todos,
                    collectionsRepository = cols,
                    today = { LocalDate.of(2026, 7, 20) },
                )

            assertEquals(MemoRelationOutcome.ADDED, toggle.toggleTodo("m1"))
            val afterAdd = memos.getMemo("m1")!!.content
            assertTrue(afterAdd.contains("<!-- xinliu:2026-07-20:TODO id="))
            assertEquals(1, todos.items.size)

            assertEquals(MemoRelationOutcome.REMOVED, toggle.toggleTodo("m1"))
            val afterRemove = memos.getMemo("m1")!!.content
            assertFalse(afterRemove.contains("xinliu:"))
            assertEquals(0, todos.items.values.count { it.deletedAt == null })
        }

    @Test
    fun toggleTodo_twice_does_not_create_two_open_items() =
        runBlocking {
            val memos = FakeMemos()
            val todos = FakeTodos()
            val cols = FakeCollections()
            memos.put(sampleMemo("m1", "任务"))
            val toggle =
                MemoRelationToggle(
                    memoRepository = memos,
                    todoRepository = todos,
                    collectionsRepository = cols,
                    today = { LocalDate.of(2026, 7, 20) },
                )
            toggle.toggleTodo("m1")
            toggle.toggleTodo("m1") // cancel
            toggle.toggleTodo("m1") // re-add
            assertEquals(1, todos.items.values.count { it.deletedAt == null })
        }

    @Test
    fun toggleFavorite_entity_without_marker_is_mark_not_duplicate() =
        runBlocking {
            val memos = FakeMemos()
            val todos = FakeTodos()
            val cols = FakeCollections()
            memos.put(sampleMemo("m1", "收藏我"))
            cols.favoriteIds["m1"] = "ref-existing"
            val toggle =
                MemoRelationToggle(
                    memoRepository = memos,
                    todoRepository = todos,
                    collectionsRepository = cols,
                    today = { LocalDate.of(2026, 7, 21) },
                )
            assertEquals(MemoRelationOutcome.MARKED, toggle.toggleFavorite("m1"))
            assertEquals(1, cols.favoriteIds.size)
            assertTrue(memos.getMemo("m1")!!.content.contains("FAV id=ref-existing"))
        }

    private class FakeMemos : MemoRepository {
        private val map = ConcurrentHashMap<String, Memo>()

        fun put(m: Memo) {
            map[m.uuid] = m
        }

        override fun observeMemos() = flowOf(map.values.toList())

        override fun observeArchivedMemos() = flowOf(emptyList<Memo>())

        override fun observeAllMemos() = flowOf(map.values.toList())

        override fun observeRecentMemos(limit: Int) = flowOf(map.values.take(limit))

        override fun observeMemosByCreatedAtRange(startInclusive: Long, endExclusive: Long) =
            flowOf(emptyList<Memo>())

        override suspend fun listRecentEditedActiveMemos(limit: Int) = emptyList<Memo>()

        override suspend fun getMemo(uuid: String) = map[uuid]

        override suspend fun archiveMemo(uuid: String) = Unit

        override suspend fun unarchiveMemo(uuid: String) = Unit

        override suspend fun setPinned(uuid: String, pinned: Boolean) = Unit

        override suspend fun updateMemoContent(uuid: String, content: String) {
            val old = map[uuid] ?: return
            map[uuid] = old.copy(content = content)
        }

        override suspend fun updateMemoContentSilent(uuid: String, content: String) {
            updateMemoContent(uuid, content)
        }

        override suspend fun createLocalMemo(content: String, resourceUris: List<String>) = ""

        override suspend fun updateLocalMemo(uuid: String, content: String, resourceUris: List<String>) = Unit

        override suspend fun updateMemoDraft(
            uuid: String,
            content: String,
            attachments: List<cc.pscly.onememos.domain.model.MemoAttachmentDraft>,
        ) = Unit
    }

    private class FakeTodos : TodoRepository {
        val items = ConcurrentHashMap<String, TodoItem>()
        private val lists = MutableStateFlow(listOf(TodoList(id = "list1", name = "待办")))

        override fun observeLists(includeArchived: Boolean, includeDeleted: Boolean) = lists

        override suspend fun getList(listId: String) = lists.value.firstOrNull { it.id == listId }

        override suspend fun createList(name: String, color: String?) = "list1"

        override suspend fun updateList(list: TodoList) = Unit

        override suspend fun deleteList(listId: String) = Unit

        override suspend fun restoreList(listId: String) = Unit

        override suspend fun reorderLists(orderedListIds: List<String>) = Unit

        override fun observeItems(
            listId: String?,
            status: String?,
            tag: String?,
            includeArchivedLists: Boolean,
            includeDeleted: Boolean,
        ): Flow<List<TodoItem>> = flowOf(items.values.toList())

        override suspend fun getItem(itemId: String) = items[itemId]

        override suspend fun createItem(item: TodoItem): String {
            items[item.id] = item
            return item.id
        }

        override suspend fun updateItem(item: TodoItem) {
            items[item.id] = item
        }

        override suspend fun deleteItem(itemId: String) {
            val old = items[itemId] ?: return
            items[itemId] = old.copy(deletedAt = "deleted")
        }

        override suspend fun restoreItem(itemId: String) = Unit

        override suspend fun setItemDone(itemId: String, done: Boolean) = Unit

        override fun observeOccurrences(itemId: String, includeDeleted: Boolean): Flow<List<TodoOccurrence>> =
            flowOf(emptyList())

        override suspend fun completeNextOccurrence(itemId: String): String? = null
    }

    private class FakeCollections : CollectionsRepository {
        val favoriteIds = ConcurrentHashMap<String, String>()

        override fun observeChildren(parentId: String?) = flowOf(emptyList<cc.pscly.onememos.domain.model.CollectionItem>())

        override fun observeAll() = flowOf(emptyList<cc.pscly.onememos.domain.model.CollectionItem>())

        override suspend fun createFolder(parentId: String?, name: String, color: String?) = ""

        override suspend fun addMemoRef(
            parentId: String?,
            memo: Memo,
            color: String?,
            displayName: String?,
        ) = ""

        override suspend fun addMemoToFavorites(memoUuid: String): String {
            val id = favoriteIds[memoUuid] ?: "ref-${memoUuid}"
            favoriteIds[memoUuid] = id
            return id
        }

        override suspend fun findFavoriteRefId(memoUuid: String): String? = favoriteIds[memoUuid]

        override suspend fun removeMemoFromFavorites(memoUuid: String) {
            favoriteIds.remove(memoUuid)
        }

        override suspend fun isFavoritesFolder(folderId: String) = folderId == "fav-folder"

        override suspend fun rename(id: String, name: String) = Unit

        override suspend fun recolor(ids: List<String>, color: String?) = Unit

        override suspend fun move(ids: List<String>, targetParentId: String?) = Unit

        override suspend fun reorder(parentId: String?, orderedIds: List<String>) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun batchDelete(ids: List<String>) = Unit

        override suspend fun backfillMemoRefId(memoUuid: String, memoServerId: String) = Unit
    }
}
