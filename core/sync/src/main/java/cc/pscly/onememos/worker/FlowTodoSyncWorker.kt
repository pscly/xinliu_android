package cc.pscly.onememos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.core.database.dao.TodoSyncDao
import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.core.database.entity.TodoListEntity
import cc.pscly.onememos.core.database.entity.TodoOccurrenceEntity
import cc.pscly.onememos.core.database.entity.TodoSyncStateEntity
import cc.pscly.onememos.core.network.FlowSyncApi
import cc.pscly.onememos.core.network.FlowSyncMutation
import cc.pscly.onememos.core.network.FlowSyncPushRequest
import cc.pscly.onememos.core.network.FlowSyncRejected
import cc.pscly.onememos.core.network.FlowSyncPullResponse
import cc.pscly.onememos.core.network.SyncTodoListOut
import cc.pscly.onememos.core.network.TodoItemOut
import cc.pscly.onememos.core.network.SyncTodoOccurrenceOut
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.domain.todo.TodoTagsTextCodec
import cc.pscly.onememos.domain.util.Hashing
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.time.Instant
import java.util.UUID

@HiltWorker
class FlowTodoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val todoDao: TodoDao,
    private val todoSyncDao: TodoSyncDao,
    private val flowSyncApi: FlowSyncApi,
    private val settingsRepository: SettingsRepository,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
    private val todoReminderScheduler: TodoReminderScheduler,
) : CoroutineWorker(appContext, params) {
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (settings.loginMode != LoginMode.BACKEND) return Result.success()
        val token = settings.token.trim()
        if (token.isBlank()) return Result.success()

        val ownerKey = currentOwnerKeyOrNull() ?: return Result.success()

        val startState = todoSyncDao.getSyncState(ownerKey) ?: TodoSyncStateEntity(ownerKey = ownerKey)
        todoSyncDao.upsertSyncState(startState.copy(running = true, lastError = null))

        val nowMs = System.currentTimeMillis()
        var cursor = startState.cursor.coerceAtLeast(0L)
        var lastError: String? = null

        val result =
            try {
                val pushedCursor = pushOutbox(ownerKey = ownerKey, token = token, cursor = cursor)
                if (pushedCursor == null) {
                    lastError = "网络异常：push 失败"
                    Result.retry()
                } else {
                    cursor = pushedCursor
                    val pulledCursor = pullChanges(ownerKey = ownerKey, token = token, cursor = cursor)
                    if (pulledCursor == null) {
                        lastError = "网络异常：pull 失败"
                        Result.retry()
                    } else {
                        cursor = pulledCursor
                        Result.success()
                    }
                }
            } catch (e: HttpException) {
                val status = e.code()
                val msg = e.message?.take(200) ?: "同步失败"
                lastError = "HTTP $status：$msg"
                // 401/403：等待用户重新登录；不重试。
                if (status == 401 || status == 403) Result.success() else Result.retry()
            } catch (e: Exception) {
                lastError = e.message?.take(200) ?: "同步失败"
                Result.retry()
            }

        todoSyncDao.upsertSyncState(
            TodoSyncStateEntity(
                ownerKey = ownerKey,
                cursor = cursor,
                running = false,
                lastSyncAtMs = nowMs,
                lastError = lastError,
            ),
        )

        if (result is Result.Success) {
            // 同步完成后重排一次提醒，确保服务端下发的 reminders/due/rrule 能落到系统通知。
            todoReminderScheduler.requestReschedule()
        }

        return result
    }

    private fun currentOwnerKeyOrNull(): String? {
        val cred = flowBackendCredentialStorage.get() ?: return null
        val username = cred.username.trim()
        if (username.isBlank()) return null
        return Hashing.sha256Hex(username.lowercase())
    }

    private suspend fun pushOutbox(
        ownerKey: String,
        token: String,
        cursor: Long,
    ): Long? {
        val pending = todoSyncDao.listPendingOutbox(ownerKey = ownerKey, limit = 200)
        if (pending.isEmpty()) return cursor

        val mutations =
            pending.mapNotNull { row ->
                val data = decodeDataJsonOrNull(row.dataJson)
                FlowSyncMutation(
                    resource = row.resource,
                    op = row.op,
                    entityId = row.entityId,
                    clientUpdatedAtMs = row.clientUpdatedAtMs,
                    data = data,
                )
            }

        val resp =
            runCatching {
                flowSyncApi.push(
                    token = "Bearer $token",
                    body = FlowSyncPushRequest(mutations = mutations),
                    requestId = UUID.randomUUID().toString(),
                )
            }.getOrNull() ?: return null

        if (!resp.isSuccessful) {
            // 401/403：不重试；其它重试
            if (resp.code() == 401 || resp.code() == 403) return cursor
            return null
        }

        val payload = resp.body() ?: return null
        val nextCursor = maxOf(cursor, payload.cursor)

        payload.applied.orEmpty().forEach { applied ->
            todoSyncDao.deleteOutbox(
                ownerKey = ownerKey,
                resource = applied.resource,
                entityId = applied.entityId,
            )
        }

        payload.rejected.orEmpty().forEach { rejected ->
            handleRejected(ownerKey = ownerKey, rejected = rejected)
        }

        return nextCursor
    }

    private suspend fun handleRejected(
        ownerKey: String,
        rejected: FlowSyncRejected,
    ) {
        val reason = rejected.reason.trim()
        val serverSnapshot = rejected.server
        if (reason.equals("conflict", ignoreCase = true) && serverSnapshot != null) {
            applyServerSnapshot(ownerKey = ownerKey, resource = rejected.resource, server = serverSnapshot)
            todoSyncDao.markOutboxState(
                ownerKey = ownerKey,
                resource = rejected.resource,
                entityId = rejected.entityId,
                state = "REJECTED_CONFLICT",
                lastError = "conflict",
            )
        } else {
            todoSyncDao.markOutboxState(
                ownerKey = ownerKey,
                resource = rejected.resource,
                entityId = rejected.entityId,
                state = "REJECTED",
                lastError = reason.ifBlank { "rejected" },
            )
        }
    }

    private suspend fun applyServerSnapshot(
        ownerKey: String,
        resource: String,
        server: Map<String, Any?>,
    ) {
        val json = gson.toJson(server)
        when (resource) {
            RESOURCE_TODO_LIST -> {
                val dto = runCatching { gson.fromJson(json, SyncTodoListOut::class.java) }.getOrNull() ?: return
                todoDao.upsertList(dto.toEntity(ownerKey))
            }

            RESOURCE_TODO_ITEM -> {
                val dto = runCatching { gson.fromJson(json, TodoItemOut::class.java) }.getOrNull() ?: return
                todoDao.upsertItem(dto.toEntity(ownerKey))
            }

            RESOURCE_TODO_OCCURRENCE -> {
                val dto = runCatching { gson.fromJson(json, SyncTodoOccurrenceOut::class.java) }.getOrNull() ?: return
                todoDao.upsertOccurrence(dto.toEntity(ownerKey))
            }
        }
    }

    private suspend fun pullChanges(
        ownerKey: String,
        token: String,
        cursor: Long,
    ): Long? {
        var current = cursor.coerceAtLeast(0L)
        var pages = 0

        while (pages < 20) {
            pages++
            val resp =
                runCatching {
                    flowSyncApi.pull(
                        token = "Bearer $token",
                        cursor = current,
                        limit = 200,
                        requestId = UUID.randomUUID().toString(),
                    )
                }.getOrNull() ?: return null

            if (!resp.isSuccessful) {
                if (resp.code() == 401 || resp.code() == 403) return current
                return null
            }

            val payload: FlowSyncPullResponse = resp.body() ?: return null
            applyChanges(ownerKey = ownerKey, payload = payload)

            current = payload.nextCursor
            todoSyncDao.upsertSyncState(
                (todoSyncDao.getSyncState(ownerKey) ?: TodoSyncStateEntity(ownerKey = ownerKey)).copy(
                    cursor = current,
                    running = true,
                    lastError = null,
                ),
            )

            if (!payload.hasMore) break
        }

        return current
    }

    private suspend fun applyChanges(
        ownerKey: String,
        payload: FlowSyncPullResponse,
    ) {
        val nowIso = Instant.now().toString()

        val listEntities =
            payload.changes.todoLists.map { it.toEntity(ownerKey, fallbackUpdatedAt = nowIso) }
        if (listEntities.isNotEmpty()) {
            todoDao.upsertLists(listEntities)
        }

        val itemEntities =
            payload.changes.todoItems.map { it.toEntity(ownerKey, fallbackUpdatedAt = nowIso) }
        if (itemEntities.isNotEmpty()) {
            todoDao.upsertItems(itemEntities)
        }

        val occEntities =
            payload.changes.todoOccurrences.map { it.toEntity(ownerKey, fallbackUpdatedAt = nowIso) }
        if (occEntities.isNotEmpty()) {
            todoDao.upsertOccurrences(occEntities)
        }
    }

    private fun SyncTodoListOut.toEntity(
        ownerKey: String,
        fallbackUpdatedAt: String = Instant.EPOCH.toString(),
    ): TodoListEntity =
        TodoListEntity(
            ownerKey = ownerKey,
            id = id,
            name = name,
            color = color,
            sortOrder = sortOrder,
            archived = archived,
            deletedAt = deletedAt,
            clientUpdatedAtMs = clientUpdatedAtMs,
            updatedAt = updatedAt.ifBlank { fallbackUpdatedAt },
        )

    private fun TodoItemOut.toEntity(
        ownerKey: String,
        fallbackUpdatedAt: String = Instant.EPOCH.toString(),
    ): TodoItemEntity =
        TodoItemEntity(
            ownerKey = ownerKey,
            id = id,
            listId = listId,
            parentId = parentId,
            title = title,
            note = note,
            status = status,
            priority = priority,
            sortOrder = sortOrder,
            tagsJson = gson.toJson(tags),
            tagsText = TodoTagsTextCodec.encode(tags),
            remindersJson = gson.toJson(reminders),
            dueAtLocal = dueAtLocal,
            completedAtLocal = completedAtLocal,
            isRecurring = isRecurring,
            rrule = rrule,
            dtstartLocal = dtstartLocal,
            tzid = tzid,
            deletedAt = deletedAt,
            clientUpdatedAtMs = clientUpdatedAtMs,
            updatedAt = updatedAt.ifBlank { fallbackUpdatedAt },
        )

    private fun SyncTodoOccurrenceOut.toEntity(
        ownerKey: String,
        fallbackUpdatedAt: String = Instant.EPOCH.toString(),
    ): TodoOccurrenceEntity =
        TodoOccurrenceEntity(
            ownerKey = ownerKey,
            id = id,
            itemId = itemId,
            tzid = tzid,
            recurrenceIdLocal = recurrenceIdLocal,
            statusOverride = statusOverride,
            titleOverride = titleOverride,
            noteOverride = noteOverride,
            dueAtOverrideLocal = dueAtOverrideLocal,
            completedAtLocal = completedAtLocal,
            deletedAt = deletedAt,
            clientUpdatedAtMs = clientUpdatedAtMs,
            updatedAt = updatedAt.ifBlank { fallbackUpdatedAt },
        )

    private fun decodeDataJsonOrNull(dataJson: String?): Map<String, Any?>? {
        val raw = dataJson?.trim().orEmpty()
        if (raw.isBlank()) return null
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return runCatching { gson.fromJson<Map<String, Any?>>(raw, type) }.getOrNull()
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "one_memos_flow_todo_sync"
        const val TAG: String = "one_memos_flow_todo_sync"

        private const val RESOURCE_TODO_LIST = "todo_list"
        private const val RESOURCE_TODO_ITEM = "todo_item"
        private const val RESOURCE_TODO_OCCURRENCE = "todo_occurrence"
    }
}
