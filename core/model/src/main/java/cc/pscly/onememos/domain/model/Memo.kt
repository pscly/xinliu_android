package cc.pscly.onememos.domain.model

data class Memo(
    val uuid: String,
    val serverId: String?,
    val creator: String?,
    val content: String,
    val plainPreview: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val serverState: MemoServerState,
    val visibility: MemoVisibility,
    val pinned: Boolean,
    val syncStatus: SyncStatus,
    val attachments: List<MemoAttachment>,
    val lastSyncError: String?,
)
