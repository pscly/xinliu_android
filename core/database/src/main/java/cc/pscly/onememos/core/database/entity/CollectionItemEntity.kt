package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "collection_items",
    primaryKeys = ["ownerKey", "id"],
    indices = [
        Index(value = ["ownerKey", "parentId", "sortOrder"]),
        Index(value = ["ownerKey", "deletedAt"]),
        Index(value = ["ownerKey", "localOnly"]),
        Index(value = ["ownerKey", "refType", "refId"]),
        Index(value = ["ownerKey", "refLocalUuid"]),
    ],
)
data class CollectionItemEntity(
    val ownerKey: String,
    val id: String,
    val itemType: String,
    val parentId: String? = null,
    val name: String = "",
    val color: String? = null,
    val refType: String? = null,
    val refId: String? = null,
    val sortOrder: Int = 0,
    val clientUpdatedAtMs: Long = 0L,
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
    val localOnly: Boolean = false,
    val refLocalUuid: String? = null,
)
