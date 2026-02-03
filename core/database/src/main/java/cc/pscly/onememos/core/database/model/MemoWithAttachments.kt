package cc.pscly.onememos.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.entity.MemoEntity

data class MemoWithAttachments(
    @Embedded val memo: MemoEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "memoUuid",
    )
    val attachments: List<MemoAttachmentEntity>,
)
