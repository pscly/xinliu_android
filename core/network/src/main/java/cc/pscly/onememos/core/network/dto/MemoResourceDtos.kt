package cc.pscly.onememos.core.network.dto

/**
 * 上游 memos v0.31.0 `Location`：可选 placeholder/latitude/longitude。
 *
 * Gson 默认不下发 null 字段，因此老服务端不认识 location 时也不会因为多余字段报错
 * （真正兼容风险在于老服务端可能忽略未知字段，这一点在 README/apidocs 里说明）。
 */
data class MemoLocationDto(
    val placeholder: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** 上游 memos v0.31.0 `Memo.Property`（只读的计算属性）。 */
data class MemoPropertyDto(
    val hasLink: Boolean? = null,
    val hasTaskList: Boolean? = null,
    val hasCode: Boolean? = null,
    val hasIncompleteTasks: Boolean? = null,
    val title: String? = null,
)

/**
 * 上游 memos v0.31.0 `MemoRelation.Memo`：关系两端对 memo 的引用。
 *
 * 注意：0.31 之前该类型叫 `MemoRelation.Memo` 且字段结构不同，这里按 0.31 对齐。
 */
data class MemoRelationRefDto(
    val name: String? = null,
    val snippet: String? = null,
)

/** 上游 memos v0.31.0 `MemoRelation`：type 取值 REFERENCE|COMMENT。 */
data class MemoRelationDto(
    val memo: MemoRelationRefDto? = null,
    val relatedMemo: MemoRelationRefDto? = null,
    val type: String? = null,
)

/** 上游 memos v0.31.0 `Reaction`（0.31 起移除了 contentId，反应只作用于所属 memo）。 */
data class ReactionDto(
    val name: String? = null,
    val creator: String? = null,
    val reactionType: String? = null,
    val createTime: String? = null,
)
