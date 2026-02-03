package cc.pscly.onememos.data.mapper

import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.model.MemoWithAttachments
import cc.pscly.onememos.domain.derived.TagsTextCodec
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachment

fun MemoWithAttachments.toDomain(): Memo =
    Memo(
        uuid = memo.uuid,
        serverId = memo.serverId,
        creator = memo.creator,
        content = memo.content,
        plainPreview = memo.plainPreview,
        tags = TagsTextCodec.decode(memo.tagsText),
        createdAt = memo.createdAt,
        updatedAt = memo.updatedAt,
        serverState = memo.serverState,
        visibility = memo.visibility,
        pinned = memo.pinned,
        syncStatus = memo.syncStatus,
        attachments =
            attachments
                .sortedWith(compareBy<MemoAttachmentEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
                .map { it.toDomain() },
        lastSyncError = memo.lastSyncError,
    )

fun MemoAttachmentEntity.toDomain(): MemoAttachment =
    MemoAttachment(
        id = id,
        localUri = localUri,
        cacheUri = cacheUri,
        remoteName = remoteName,
        filename = filename,
        mimeType = mimeType,
        createdAt = createdAt,
    )
