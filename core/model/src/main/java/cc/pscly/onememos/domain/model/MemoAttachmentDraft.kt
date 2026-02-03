package cc.pscly.onememos.domain.model

/**
 * 用于“编辑记录”时的附件草稿：
 * - localUri：本地附件（content:// 或 file://）
 * - remoteName：已绑定到服务端的附件名（例如 attachments/xxx）
 *
 * 说明：排序由“列表顺序”表达，数据层会按索引写入 sortOrder。
 */
data class MemoAttachmentDraft(
    val localUri: String?,
    val remoteName: String?,
    val filename: String?,
    val mimeType: String?,
    val createdAt: Long,
)
