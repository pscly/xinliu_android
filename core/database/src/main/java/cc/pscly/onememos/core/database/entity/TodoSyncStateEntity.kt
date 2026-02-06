package cc.pscly.onememos.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "todo_sync_state",
    primaryKeys = ["ownerKey"],
)
data class TodoSyncStateEntity(
    val ownerKey: String,
    val cursor: Long = 0L,
    val running: Boolean = false,
    val lastSyncAtMs: Long = 0L,
    val lastError: String? = null,
)

