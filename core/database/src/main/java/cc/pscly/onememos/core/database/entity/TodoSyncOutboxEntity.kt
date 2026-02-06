package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "todo_sync_outbox",
    primaryKeys = ["ownerKey", "resource", "entityId"],
    indices = [
        Index(value = ["ownerKey", "state", "createdAtMs"]),
    ],
)
data class TodoSyncOutboxEntity(
    val ownerKey: String,
    val resource: String,
    val entityId: String,
    val op: String,
    val clientUpdatedAtMs: Long,
    val dataJson: String? = null,
    val state: String = "PENDING",
    val lastError: String? = null,
    val createdAtMs: Long = 0L,
)

