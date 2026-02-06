@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.pscly.onememos.data.repository

import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.core.database.dao.TodoSyncDao
import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.core.database.entity.TodoListEntity
import cc.pscly.onememos.core.database.entity.TodoOccurrenceEntity
import cc.pscly.onememos.core.database.entity.TodoSyncOutboxEntity
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.data.mapper.toDomain
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoOccurrence
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.domain.todo.TodoRecurrenceCalculator
import cc.pscly.onememos.domain.todo.TodoTagsTextCodec
import cc.pscly.onememos.domain.util.Hashing
import cc.pscly.onememos.domain.util.LocalDateTimes
import cc.pscly.onememos.domain.util.TodoOccurrenceIds
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val todoDao: TodoDao,
    private val todoSyncDao: TodoSyncDao,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: TodoSyncScheduler,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : TodoRepository {
    private val gson = Gson()

    private fun currentOwnerKeyOrNull(): String? {
        val cred = flowBackendCredentialStorage.get() ?: return null
        val username = cred.username.trim()
        if (username.isBlank()) return null
        // 用户名仅字母数字，但仍做一次规范化，避免大小写导致重复分区。
        return Hashing.sha256Hex(username.lowercase())
    }

    private suspend fun isTodoEnabled(): Boolean {
        val s = settingsRepository.settings.first()
        if (s.loginMode != LoginMode.BACKEND) return false
        if (s.token.trim().isBlank()) return false
        return currentOwnerKeyOrNull() != null
    }

    override fun observeLists(
        includeArchived: Boolean,
        includeDeleted: Boolean,
    ): Flow<List<TodoList>> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { ok ->
                val ownerKey = currentOwnerKeyOrNull()
                if (!ok || ownerKey == null) {
                    flowOf(emptyList())
                } else {
                    todoDao.observeLists(
                        ownerKey = ownerKey,
                        includeArchived = includeArchived,
                        includeDeleted = includeDeleted,
                    )
                        .map { list -> list.map { it.toDomain() } }
                }
            }

    override suspend fun getList(listId: String): TodoList? {
        val ownerKey = currentOwnerKeyOrNull() ?: return null
        return todoDao.getList(ownerKey, listId)?.toDomain()
    }

    override suspend fun createList(name: String, color: String?): String {
        if (!isTodoEnabled()) return ""
        val ownerKey = currentOwnerKeyOrNull() ?: return ""

        val nowMs = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val updatedAt = Instant.now().toString()

        val list =
            TodoListEntity(
                ownerKey = ownerKey,
                id = id,
                name = name.trim().ifBlank { "未命名清单" },
                color = color?.trim()?.takeIf { it.isNotBlank() },
                sortOrder = 0,
                archived = false,
                deletedAt = null,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )

        todoDao.upsertList(list)
        enqueueListUpsert(ownerKey = ownerKey, list = list, nowMs = nowMs)
        syncScheduler.requestSync()
        return id
    }

    override suspend fun updateList(list: TodoList) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return

        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val entity =
            TodoListEntity(
                ownerKey = ownerKey,
                id = list.id,
                name = list.name.trim().ifBlank { "未命名清单" },
                color = list.color?.trim()?.takeIf { it.isNotBlank() },
                sortOrder = list.sortOrder,
                archived = list.archived,
                deletedAt = list.deletedAt,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )
        todoDao.upsertList(entity)
        enqueueListUpsert(ownerKey = ownerKey, list = entity, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun deleteList(listId: String) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getList(ownerKey, listId) ?: return
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val deletedAt = updatedAt
        todoDao.upsertList(
            existing.copy(
                deletedAt = deletedAt,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            ),
        )
        enqueueDelete(ownerKey = ownerKey, resource = RESOURCE_TODO_LIST, entityId = listId, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun restoreList(listId: String) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getList(ownerKey, listId) ?: return
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val restored =
            existing.copy(
                deletedAt = null,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )
        todoDao.upsertList(restored)
        enqueueListUpsert(ownerKey = ownerKey, list = restored, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun reorderLists(orderedListIds: List<String>) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()

        orderedListIds.forEachIndexed { index, id ->
            val existing = todoDao.getList(ownerKey, id) ?: return@forEachIndexed
            val next =
                existing.copy(
                    sortOrder = index,
                    clientUpdatedAtMs = nowMs,
                    updatedAt = updatedAt,
                )
            todoDao.upsertList(next)
            enqueueListUpsert(ownerKey = ownerKey, list = next, nowMs = nowMs)
        }
        syncScheduler.requestSync()
    }

    override fun observeItems(
        listId: String?,
        status: String?,
        tag: String?,
        includeArchivedLists: Boolean,
        includeDeleted: Boolean,
    ): Flow<List<TodoItem>> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { ok ->
                val ownerKey = currentOwnerKeyOrNull()
                if (!ok || ownerKey == null) {
                    flowOf(emptyList())
                } else {
                    val needle = tag?.trim()?.takeIf { it.isNotBlank() }?.let(TodoTagsTextCodec::needle)
                    todoDao.observeItems(
                        ownerKey = ownerKey,
                        listId = listId?.trim()?.takeIf { it.isNotBlank() },
                        status = status?.trim()?.takeIf { it.isNotBlank() },
                        includeArchivedLists = includeArchivedLists,
                        includeDeleted = includeDeleted,
                        tagNeedle = needle,
                    )
                        .map { list -> list.map { it.toDomain() } }
                }
            }

    override suspend fun getItem(itemId: String): TodoItem? {
        val ownerKey = currentOwnerKeyOrNull() ?: return null
        return todoDao.getItem(ownerKey, itemId)?.toDomain()
    }

    override suspend fun createItem(item: TodoItem): String {
        if (!isTodoEnabled()) return ""
        val ownerKey = currentOwnerKeyOrNull() ?: return ""

        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val id = item.id.trim().ifBlank { UUID.randomUUID().toString() }
        val tzid =
            item.tzid.trim().ifBlank {
                runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty()
            }
        val tags = item.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val tagsJson = gson.toJson(tags)
        val tagsText = TodoTagsTextCodec.encode(tags)
        val remindersJson = normalizeJsonArray(item.remindersJson)

        val entity =
            TodoItemEntity(
                ownerKey = ownerKey,
                id = id,
                listId = item.listId.trim(),
                parentId = item.parentId?.trim()?.takeIf { it.isNotBlank() },
                title = item.title.trim().ifBlank { "未命名任务" },
                note = item.note,
                status = item.status.trim().ifBlank { TodoStatuses.OPEN },
                priority = item.priority,
                sortOrder = item.sortOrder,
                tagsJson = tagsJson,
                tagsText = tagsText,
                remindersJson = remindersJson,
                dueAtLocal = item.dueAtLocal?.trim()?.takeIf { it.isNotBlank() },
                completedAtLocal = item.completedAtLocal?.trim()?.takeIf { it.isNotBlank() },
                isRecurring = item.isRecurring,
                rrule = item.rrule?.trim()?.takeIf { it.isNotBlank() },
                dtstartLocal = item.dtstartLocal?.trim()?.takeIf { it.isNotBlank() },
                tzid = tzid,
                deletedAt = item.deletedAt?.trim()?.takeIf { it.isNotBlank() },
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )

        todoDao.upsertItem(entity)
        enqueueItemUpsert(ownerKey = ownerKey, item = entity, nowMs = nowMs)
        syncScheduler.requestSync()
        return id
    }

    override suspend fun updateItem(item: TodoItem) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getItem(ownerKey, item.id) ?: return

        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val tzid =
            item.tzid.trim().ifBlank {
                existing.tzid.trim().ifBlank { runCatching { ZoneId.systemDefault().id }.getOrNull().orEmpty() }
            }
        val tags = item.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val tagsJson = gson.toJson(tags)
        val tagsText = TodoTagsTextCodec.encode(tags)
        val remindersJson = normalizeJsonArray(item.remindersJson)

        val next =
            existing.copy(
                listId = item.listId.trim().ifBlank { existing.listId },
                parentId = item.parentId?.trim()?.takeIf { it.isNotBlank() },
                title = item.title.trim().ifBlank { existing.title },
                note = item.note,
                status = item.status.trim().ifBlank { existing.status },
                priority = item.priority,
                sortOrder = item.sortOrder,
                tagsJson = tagsJson,
                tagsText = tagsText,
                remindersJson = remindersJson,
                dueAtLocal = item.dueAtLocal?.trim()?.takeIf { it.isNotBlank() },
                completedAtLocal = item.completedAtLocal?.trim()?.takeIf { it.isNotBlank() },
                isRecurring = item.isRecurring,
                rrule = item.rrule?.trim()?.takeIf { it.isNotBlank() },
                dtstartLocal = item.dtstartLocal?.trim()?.takeIf { it.isNotBlank() },
                tzid = tzid,
                deletedAt = item.deletedAt?.trim()?.takeIf { it.isNotBlank() },
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )
        todoDao.upsertItem(next)
        enqueueItemUpsert(ownerKey = ownerKey, item = next, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun deleteItem(itemId: String) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getItem(ownerKey, itemId) ?: return
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        todoDao.upsertItem(
            existing.copy(
                deletedAt = updatedAt,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            ),
        )
        enqueueDelete(ownerKey = ownerKey, resource = RESOURCE_TODO_ITEM, entityId = itemId, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun restoreItem(itemId: String) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getItem(ownerKey, itemId) ?: return
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val restored =
            existing.copy(
                deletedAt = null,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )
        todoDao.upsertItem(restored)
        enqueueItemUpsert(ownerKey = ownerKey, item = restored, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override suspend fun setItemDone(itemId: String, done: Boolean) {
        if (!isTodoEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val existing = todoDao.getItem(ownerKey, itemId) ?: return

        // 循环任务默认用 occurrence 完成；避免把整个 item 直接变成 done 导致语义混乱。
        if (existing.isRecurring) return

        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()
        val tzid = existing.tzid.trim().ifBlank { ZoneId.systemDefault().id }
        val completedAtLocal = if (done) LocalDateTimes.nowString(tzid) else null
        val status = if (done) TodoStatuses.DONE else TodoStatuses.OPEN

        val next =
            existing.copy(
                status = status,
                completedAtLocal = completedAtLocal,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )
        todoDao.upsertItem(next)
        enqueueItemUpsert(ownerKey = ownerKey, item = next, nowMs = nowMs)
        syncScheduler.requestSync()
    }

    override fun observeOccurrences(
        itemId: String,
        includeDeleted: Boolean,
    ): Flow<List<TodoOccurrence>> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { ok ->
                val ownerKey = currentOwnerKeyOrNull()
                if (!ok || ownerKey == null) {
                    flowOf(emptyList())
                } else {
                    todoDao.observeOccurrences(
                        ownerKey = ownerKey,
                        itemId = itemId,
                        includeDeleted = includeDeleted,
                    )
                        .map { list -> list.map { it.toDomain() } }
                }
            }

    override suspend fun completeNextOccurrence(itemId: String): String? {
        if (!isTodoEnabled()) return null
        val ownerKey = currentOwnerKeyOrNull() ?: return null
        val item = todoDao.getItem(ownerKey, itemId) ?: return null
        if (!item.isRecurring) return null

        val tzid = item.tzid.trim().ifBlank { ZoneId.systemDefault().id }
        val nowLocal = LocalDateTimes.nowString(tzid)
        val nextRecurrenceIdLocal =
            TodoRecurrenceCalculator.nextRecurrenceIdLocal(
                rrule = item.rrule,
                dtstartLocal = item.dtstartLocal,
                nowLocal = nowLocal,
            )
                ?: return null

        val occurrenceId = TodoOccurrenceIds.stableId(itemId = itemId, tzid = tzid, recurrenceIdLocal = nextRecurrenceIdLocal)
        val nowMs = System.currentTimeMillis()
        val updatedAt = Instant.now().toString()

        val occurrence =
            TodoOccurrenceEntity(
                ownerKey = ownerKey,
                id = occurrenceId,
                itemId = itemId,
                tzid = tzid,
                recurrenceIdLocal = nextRecurrenceIdLocal,
                statusOverride = TodoStatuses.DONE,
                titleOverride = null,
                noteOverride = null,
                dueAtOverrideLocal = null,
                completedAtLocal = nowLocal,
                deletedAt = null,
                clientUpdatedAtMs = nowMs,
                updatedAt = updatedAt,
            )

        todoDao.upsertOccurrence(occurrence)
        enqueueOccurrenceUpsert(ownerKey = ownerKey, occurrence = occurrence, nowMs = nowMs)
        syncScheduler.requestSync()
        return occurrenceId
    }

    private suspend fun enqueueListUpsert(
        ownerKey: String,
        list: TodoListEntity,
        nowMs: Long,
    ) {
        val data =
            mapOf(
                "name" to list.name,
                "color" to list.color,
                "sort_order" to list.sortOrder,
                "archived" to list.archived,
            )
        enqueueUpsert(
            ownerKey = ownerKey,
            resource = RESOURCE_TODO_LIST,
            entityId = list.id,
            clientUpdatedAtMs = list.clientUpdatedAtMs,
            createdAtMs = nowMs,
            data = data,
        )
    }

    private suspend fun enqueueItemUpsert(
        ownerKey: String,
        item: TodoItemEntity,
        nowMs: Long,
    ) {
        val data =
            buildMap<String, Any?> {
                put("list_id", item.listId)
                put("parent_id", item.parentId)
                put("title", item.title)
                put("note", item.note)
                put("status", item.status)
                put("priority", item.priority)
                put("sort_order", item.sortOrder)
                put("tags", decodeTagsJson(item.tagsJson))
                put("reminders", decodeJsonAnyOrEmptyList(item.remindersJson))
                put("due_at_local", item.dueAtLocal)
                put("completed_at_local", item.completedAtLocal)
                put("is_recurring", item.isRecurring)
                put("rrule", item.rrule)
                put("dtstart_local", item.dtstartLocal)
                put("tzid", item.tzid)
            }

        enqueueUpsert(
            ownerKey = ownerKey,
            resource = RESOURCE_TODO_ITEM,
            entityId = item.id,
            clientUpdatedAtMs = item.clientUpdatedAtMs,
            createdAtMs = nowMs,
            data = data,
        )
    }

    private suspend fun enqueueOccurrenceUpsert(
        ownerKey: String,
        occurrence: TodoOccurrenceEntity,
        nowMs: Long,
    ) {
        val data =
            buildMap<String, Any?> {
                put("item_id", occurrence.itemId)
                put("tzid", occurrence.tzid)
                put("recurrence_id_local", occurrence.recurrenceIdLocal)
                put("status_override", occurrence.statusOverride)
                put("title_override", occurrence.titleOverride)
                put("note_override", occurrence.noteOverride)
                put("due_at_override_local", occurrence.dueAtOverrideLocal)
                put("completed_at_local", occurrence.completedAtLocal)
            }
        enqueueUpsert(
            ownerKey = ownerKey,
            resource = RESOURCE_TODO_OCCURRENCE,
            entityId = occurrence.id,
            clientUpdatedAtMs = occurrence.clientUpdatedAtMs,
            createdAtMs = nowMs,
            data = data,
        )
    }

    private suspend fun enqueueDelete(
        ownerKey: String,
        resource: String,
        entityId: String,
        nowMs: Long,
    ) {
        val outbox =
            TodoSyncOutboxEntity(
                ownerKey = ownerKey,
                resource = resource,
                entityId = entityId,
                op = OP_DELETE,
                clientUpdatedAtMs = nowMs,
                dataJson = null,
                state = STATE_PENDING,
                lastError = null,
                createdAtMs = nowMs,
        )
        // REPLACE：同一实体多次 delete 也只保留最后一次（避免 outbox 膨胀）。
        todoSyncDao.upsertOutbox(outbox)
    }

    private suspend fun enqueueUpsert(
        ownerKey: String,
        resource: String,
        entityId: String,
        clientUpdatedAtMs: Long,
        createdAtMs: Long,
        data: Map<String, Any?>,
    ) {
        val outbox =
            TodoSyncOutboxEntity(
                ownerKey = ownerKey,
                resource = resource,
                entityId = entityId,
                op = OP_UPSERT,
                clientUpdatedAtMs = clientUpdatedAtMs,
                dataJson = gson.toJson(data),
                state = STATE_PENDING,
                lastError = null,
                createdAtMs = createdAtMs,
            )
        todoSyncDao.upsertOutbox(outbox)
    }

    private fun normalizeJsonArray(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "[]"
        val parsed = runCatching { gson.fromJson(trimmed, Any::class.java) }.getOrNull()
        return if (parsed is List<*>) trimmed else "[]"
    }

    private fun decodeTagsJson(tagsJson: String): List<String> {
        val t = tagsJson.trim()
        if (t.isBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(t, type) }.getOrNull().orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun decodeJsonAnyOrEmptyList(rawJson: String): List<Any?> {
        val t = rawJson.trim()
        if (t.isBlank()) return emptyList()
        val parsed = runCatching { gson.fromJson(t, Any::class.java) }.getOrNull()
        return (parsed as? List<*>)?.toList() ?: emptyList()
    }

    private companion object {
        private const val RESOURCE_TODO_LIST = "todo_list"
        private const val RESOURCE_TODO_ITEM = "todo_item"
        private const val RESOURCE_TODO_OCCURRENCE = "todo_occurrence"

        private const val OP_UPSERT = "upsert"
        private const val OP_DELETE = "delete"

        private const val STATE_PENDING = "PENDING"
    }
}
