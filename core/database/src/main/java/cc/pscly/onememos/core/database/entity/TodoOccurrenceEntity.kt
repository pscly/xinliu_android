package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "todo_occurrences",
    primaryKeys = ["ownerKey", "id"],
    indices = [
        Index(value = ["ownerKey", "itemId", "recurrenceIdLocal"]),
        Index(value = ["ownerKey", "deletedAt"]),
        Index(
            value = ["ownerKey", "itemId", "tzid", "recurrenceIdLocal"],
            unique = true,
        ),
    ],
)
data class TodoOccurrenceEntity(
    val ownerKey: String,
    val id: String,
    val itemId: String,
    val tzid: String,
    val recurrenceIdLocal: String,
    val statusOverride: String? = null,
    val titleOverride: String? = null,
    val noteOverride: String? = null,
    val dueAtOverrideLocal: String? = null,
    val completedAtLocal: String? = null,
    val deletedAt: String? = null,
    val clientUpdatedAtMs: Long,
    val updatedAt: String,
)

