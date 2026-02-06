package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoOccurrence
import kotlinx.coroutines.flow.Flow

/**
 * Todo（待办）仓库接口：
 * - 本地优先：UI 只订阅本地数据（Room），写操作也先落库。
 * - 同步：实现层会把写入意图写入 outbox，并由 Worker 统一 push/pull。
 */
interface TodoRepository {
    fun observeLists(includeArchived: Boolean): Flow<List<TodoList>>

    suspend fun getList(listId: String): TodoList?

    suspend fun createList(
        name: String,
        color: String? = null,
    ): String

    suspend fun updateList(list: TodoList)

    /**
     * 软删除清单（不会立刻物理删除本地数据；同步后由服务端下发 deleted_at）。
     */
    suspend fun deleteList(listId: String)

    suspend fun restoreList(listId: String)

    /**
     * 以传入顺序重排 sortOrder（0..n-1）。
     * 注意：这是“最终排序结果”，而不是增量移动。
     */
    suspend fun reorderLists(orderedListIds: List<String>)

    fun observeItems(
        listId: String? = null,
        status: String? = null,
        tag: String? = null,
        includeArchivedLists: Boolean = false,
        includeDeleted: Boolean = false,
    ): Flow<List<TodoItem>>

    suspend fun getItem(itemId: String): TodoItem?

    suspend fun createItem(item: TodoItem): String

    suspend fun updateItem(item: TodoItem)

    suspend fun deleteItem(itemId: String)

    suspend fun restoreItem(itemId: String)

    /**
     * 非循环任务：直接切换 item 的完成状态。
     * 循环任务：实现层可选择拒绝（交由 occurrence 完成）。
     */
    suspend fun setItemDone(
        itemId: String,
        done: Boolean,
    )

    fun observeOccurrences(
        itemId: String,
        includeDeleted: Boolean = false,
    ): Flow<List<TodoOccurrence>>

    /**
     * 循环任务：计算下一次 occurrence，并标记为完成（upsert todo_occurrence）。
     * 返回值：生成的 occurrenceId（方便 UI 反馈），失败返回 null。
     */
    suspend fun completeNextOccurrence(itemId: String): String?
}

