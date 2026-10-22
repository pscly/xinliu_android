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
    /**
     * 创建时附加的位置信息（上游 memos v0.31.0 的 `Location`）。
     * null 表示该笔记没有位置信息；老数据升级后统一为 null。
     */
    val location: MemoLocation? = null,
)
