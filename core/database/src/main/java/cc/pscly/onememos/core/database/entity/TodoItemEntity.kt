package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "todo_items",
    primaryKeys = ["ownerKey", "id"],
    indices = [
        Index(value = ["ownerKey", "listId", "status", "sortOrder"]),
        Index(value = ["ownerKey", "deletedAt"]),
        Index(value = ["ownerKey", "clientUpdatedAtMs"]),
    ],
)
data class TodoItemEntity(
    val ownerKey: String,
    val id: String,
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val note: String,
    val status: String,
    val priority: Int,
    val sortOrder: Int,
    val tagsJson: String,
    /**
     * 用于本地 tag 精确匹配的辅助字段：
     * - 结构："\n<tag1>\n<tag2>\n"
     * - 查询：LIKE "%\n$tag\n%"
     */
    val tagsText: String,
    val remindersJson: String,
    val dueAtLocal: String? = null,
    val completedAtLocal: String? = null,
    val isRecurring: Boolean,
    val rrule: String? = null,
    val dtstartLocal: String? = null,
    val tzid: String,
    val deletedAt: String? = null,
    val clientUpdatedAtMs: Long,
    val updatedAt: String,
)

