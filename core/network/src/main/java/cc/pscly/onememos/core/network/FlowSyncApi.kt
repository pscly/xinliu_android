package cc.pscly.onememos.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Flow Backend 的 Sync v1（/api/v1/sync/...）。
 *
 * 用途：Todo 的离线同步（本地 outbox -> push；cursor 增量 pull）。
 */
data class FlowSyncPullResponse(
    val cursor: Long,
    @SerializedName("next_cursor")
    val nextCursor: Long,
    @SerializedName("has_more")
    val hasMore: Boolean,
    val changes: FlowSyncChanges,
)

data class FlowSyncChanges(
    @SerializedName("todo_lists")
    val todoLists: List<SyncTodoListOut> = emptyList(),
    @SerializedName("todo_items")
    val todoItems: List<TodoItemOut> = emptyList(),
    @SerializedName("todo_occurrences")
    val todoOccurrences: List<SyncTodoOccurrenceOut> = emptyList(),
    @SerializedName("collection_items")
    val collectionItems: List<CollectionItemOut> = emptyList(),
)

data class CollectionItemOut(
    val id: String,
    @SerializedName("item_type")
    val itemType: String,
    @SerializedName("parent_id")
    val parentId: String? = null,
    val name: String = "",
    val color: String? = null,
    @SerializedName("ref_type")
    val refType: String? = null,
    @SerializedName("ref_id")
    val refId: String? = null,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
    @SerializedName("client_updated_at_ms")
    val clientUpdatedAtMs: Long = 0L,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class SyncTodoListOut(
    val id: String,
    val name: String,
    @SerializedName("sort_order")
    val sortOrder: Int,
    val archived: Boolean,
    @SerializedName("client_updated_at_ms")
    val clientUpdatedAtMs: Long,
    @SerializedName("updated_at")
    val updatedAt: String,
    val color: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class TodoItemOut(
    val id: String,
    @SerializedName("list_id")
    val listId: String,
    @SerializedName("parent_id")
    val parentId: String? = null,
    val title: String,
    val note: String,
    val status: String,
    val priority: Int,
    @SerializedName("sort_order")
    val sortOrder: Int,
    val tags: List<String> = emptyList(),
    val reminders: List<Map<String, Any?>> = emptyList(),
    @SerializedName("due_at_local")
    val dueAtLocal: String? = null,
    @SerializedName("completed_at_local")
    val completedAtLocal: String? = null,
    @SerializedName("is_recurring")
    val isRecurring: Boolean,
    val rrule: String? = null,
    @SerializedName("dtstart_local")
    val dtstartLocal: String? = null,
    val tzid: String,
    @SerializedName("client_updated_at_ms")
    val clientUpdatedAtMs: Long,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class SyncTodoOccurrenceOut(
    val id: String,
    @SerializedName("item_id")
    val itemId: String,
    val tzid: String,
    @SerializedName("recurrence_id_local")
    val recurrenceIdLocal: String,
    @SerializedName("client_updated_at_ms")
    val clientUpdatedAtMs: Long,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("status_override")
    val statusOverride: String? = null,
    @SerializedName("title_override")
    val titleOverride: String? = null,
    @SerializedName("note_override")
    val noteOverride: String? = null,
    @SerializedName("due_at_override_local")
    val dueAtOverrideLocal: String? = null,
    @SerializedName("completed_at_local")
    val completedAtLocal: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class FlowSyncMutation(
    val resource: String,
    val op: String,
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("client_updated_at_ms")
    val clientUpdatedAtMs: Long = 0L,
    val data: Map<String, Any?>? = null,
)

data class FlowSyncPushRequest(
    val mutations: List<FlowSyncMutation> = emptyList(),
)

data class FlowSyncApplied(
    val resource: String,
    @SerializedName("entity_id")
    val entityId: String,
)

data class FlowSyncRejected(
    val resource: String,
    @SerializedName("entity_id")
    val entityId: String,
    val reason: String,
    val server: Map<String, Any?>? = null,
)

data class FlowSyncPushResponse(
    val cursor: Long,
    val applied: List<FlowSyncApplied>? = null,
    val rejected: List<FlowSyncRejected>? = null,
)

interface FlowSyncApi {
    @GET("/api/v1/sync/pull")
    suspend fun pull(
        @Header("Authorization") token: String,
        @Query("cursor") cursor: Long = 0,
        @Query("limit") limit: Int = 200,
        @Header("X-Request-Id") requestId: String? = null,
    ): Response<FlowSyncPullResponse>

    @POST("/api/v1/sync/push")
    suspend fun push(
        @Header("Authorization") token: String,
        @Body body: FlowSyncPushRequest,
        @Header("X-Request-Id") requestId: String? = null,
    ): Response<FlowSyncPushResponse>
}
