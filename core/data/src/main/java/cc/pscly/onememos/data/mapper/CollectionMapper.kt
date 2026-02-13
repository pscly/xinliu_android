package cc.pscly.onememos.data.mapper

import cc.pscly.onememos.core.database.entity.CollectionItemEntity
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import cc.pscly.onememos.domain.model.CollectionRefType

fun CollectionItemEntity.toDomain(): CollectionItem =
    CollectionItem(
        id = id,
        itemType = if (itemType == "folder") CollectionItemType.FOLDER else CollectionItemType.NOTE_REF,
        parentId = parentId,
        name = name,
        color = color,
        refType =
            when (refType) {
                "flow_note" -> CollectionRefType.FLOW_NOTE
                "memos_memo" -> CollectionRefType.MEMOS_MEMO
                else -> null
            },
        refId = refId,
        sortOrder = sortOrder,
        clientUpdatedAtMs = clientUpdatedAtMs,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        localOnly = localOnly,
        refLocalUuid = refLocalUuid,
    )
