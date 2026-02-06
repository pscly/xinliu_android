package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "todo_lists",
    primaryKeys = ["ownerKey", "id"],
    indices = [
        Index(value = ["ownerKey", "archived", "sortOrder"]),
        Index(value = ["ownerKey", "deletedAt"]),
    ],
)
data class TodoListEntity(
    val ownerKey: String,
    val id: String,
    val name: String,
    val color: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val deletedAt: String? = null,
    val clientUpdatedAtMs: Long = 0L,
    val updatedAt: String = "",
)

