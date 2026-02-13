package cc.pscly.onememos.domain.model

enum class CollectionItemType {
    FOLDER,
    NOTE_REF,
}

enum class CollectionRefType {
    FLOW_NOTE,
    MEMOS_MEMO,
}

data class CollectionItem(
    val id: String,
    val itemType: CollectionItemType,
    val parentId: String?,
    val name: String,
    val color: String?,
    val refType: CollectionRefType?,
    val refId: String?,
    val sortOrder: Int,
    val clientUpdatedAtMs: Long,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
    val localOnly: Boolean,
    val refLocalUuid: String?,
)
