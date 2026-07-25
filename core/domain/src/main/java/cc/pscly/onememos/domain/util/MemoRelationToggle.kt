package cc.pscly.onememos.domain.util

import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * 随笔 ↔ 待办/内置收藏 开关（ADR 0013）。
 * 先实体后注释；写注释失败则补偿撤销刚建实体。
 */
class MemoRelationToggle(
    private val memoRepository: MemoRepository,
    private val todoRepository: TodoRepository,
    private val collectionsRepository: CollectionsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    fun hasTodoMarker(content: String): Boolean =
        XinliuRelationMarker.has(content, XinliuRelationMarker.Kind.TODO)

    fun hasFavMarker(content: String): Boolean =
        XinliuRelationMarker.has(content, XinliuRelationMarker.Kind.FAV)

    suspend fun toggleTodo(memoUuid: String): MemoRelationOutcome {
        val memo = memoRepository.getMemo(memoUuid) ?: error("memo not found")
        val content = memo.content
        val marker = XinliuRelationMarker.find(content, XinliuRelationMarker.Kind.TODO)

        if (marker != null) {
            val item = todoRepository.getItem(marker.id)
            if (item != null && item.deletedAt == null) {
                todoRepository.deleteItem(item.id)
                writeContent(
                    memo.uuid,
                    XinliuRelationMarker.strip(content, XinliuRelationMarker.Kind.TODO),
                )
                return MemoRelationOutcome.REMOVED
            }
            // 注释失效 → 清注释后重建
            val cleared = XinliuRelationMarker.strip(content, XinliuRelationMarker.Kind.TODO)
            writeContent(memo.uuid, cleared)
            createTodoLinked(memo.uuid, cleared)
            return MemoRelationOutcome.READDED
        }

        createTodoLinked(memo.uuid, content)
        return MemoRelationOutcome.ADDED
    }

    suspend fun toggleFavorite(memoUuid: String): MemoRelationOutcome {
        val memo = memoRepository.getMemo(memoUuid) ?: error("memo not found")
        val content = memo.content
        val marker = XinliuRelationMarker.find(content, XinliuRelationMarker.Kind.FAV)
        val entityRefId = collectionsRepository.findFavoriteRefId(memo.uuid)

        // 已绑定：有有效注释（实体仍在）或无注释但实体在
        if (marker != null) {
            // 以实体为准：有实体则强取消
            if (entityRefId != null) {
                collectionsRepository.removeMemoFromFavorites(memo.uuid)
                writeContent(
                    memo.uuid,
                    XinliuRelationMarker.strip(content, XinliuRelationMarker.Kind.FAV),
                )
                return MemoRelationOutcome.REMOVED
            }
            // 注释失效
            val cleared = XinliuRelationMarker.strip(content, XinliuRelationMarker.Kind.FAV)
            writeContent(memo.uuid, cleared)
            addFavoriteLinked(memo.uuid, cleared)
            return MemoRelationOutcome.READDED
        }

        if (entityRefId != null) {
            val date = today().toString()
            val next =
                XinliuRelationMarker.upsert(
                    content,
                    XinliuRelationMarker.Kind.FAV,
                    date,
                    entityRefId,
                )
            writeContent(memo.uuid, next)
            return MemoRelationOutcome.MARKED
        }

        addFavoriteLinked(memo.uuid, content)
        return MemoRelationOutcome.ADDED
    }

    private suspend fun createTodoLinked(memoUuid: String, content: String) {
        val listId =
            todoRepository.observeLists(includeArchived = false).first()
                .firstOrNull { it.deletedAt == null }
                ?.id
                ?: todoRepository.createList(name = "待办")

        val titleSource = XinliuRelationMarker.stripAllForRead(content)
        val title =
            titleSource
                .trim()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .take(40)
                .ifBlank { "未命名任务" }

        val tzid = runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty()
        val itemId = UUID.randomUUID().toString()
        todoRepository.createItem(
            TodoItem(
                id = itemId,
                listId = listId,
                title = title,
                tzid = tzid,
            ),
        )
        val date = today().toString()
        val next =
            XinliuRelationMarker.upsert(
                content,
                XinliuRelationMarker.Kind.TODO,
                date,
                itemId,
            )
        try {
            writeContent(memoUuid, next)
        } catch (t: Throwable) {
            runCatching { todoRepository.deleteItem(itemId) }
            throw t
        }
    }

    private suspend fun addFavoriteLinked(memoUuid: String, content: String) {
        val newRefId = collectionsRepository.addMemoToFavorites(memoUuid)
        if (newRefId.isBlank()) error("favorites unavailable")
        val date = today().toString()
        val next =
            XinliuRelationMarker.upsert(
                content,
                XinliuRelationMarker.Kind.FAV,
                date,
                newRefId,
            )
        try {
            writeContent(memoUuid, next)
        } catch (t: Throwable) {
            runCatching { collectionsRepository.removeMemoFromFavorites(memoUuid) }
            throw t
        }
    }

    private suspend fun writeContent(memoUuid: String, content: String) {
        memoRepository.updateMemoContentSilent(memoUuid, content)
    }
}
