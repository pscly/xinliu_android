package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 上游 memos v0.31.0 `Memo` 资源（列表/详情/创建/更新的统一 HTTP body）。
 *
 * 对齐说明（0.31.0 `proto/api/v1/memo_service.proto`）：
 * - 新增 `tags`/`snippet`/`property`/`relations`/`reactions`/`location`/`parent`；
 * - `displayTime` 在 0.31 已被 `reserved`，这里保留字段仅为兼容旧服务端返回（有就读，没有就 null）。
 */
data class MemoDto(
    val name: String? = null,
    val state: String? = null,
    val creator: String? = null,
    @SerializedName("createTime")
    val createTime: String? = null,
    @SerializedName("updateTime")
    val updateTime: String? = null,
    @Deprecated("上游 0.31.0 已移除 display_time，仅为兼容旧服务端保留")
    @SerializedName("displayTime")
    val displayTime: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    /** 服务端从正文提取的标签（只读）。 */
    val tags: List<String>? = null,
    /** 正文摘要，纯文本（只读）。 */
    val snippet: String? = null,
    val pinned: Boolean? = null,
    /** 评论类 memo 的父 memo 资源名（只读）。 */
    val parent: String? = null,
    /** 计算属性（只读）。 */
    val property: MemoPropertyDto? = null,
    /** 位置信息（0.31 新增）。 */
    val location: MemoLocationDto? = null,
    val attachments: List<AttachmentDto>? = null,
    val relations: List<MemoRelationDto>? = null,
    val reactions: List<ReactionDto>? = null,
)
