package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 笔记子资源（评论/反应/关系/分享/链接预览/附件列表）的请求与响应体。
 *
 * 全部字段名取自上游 memos v0.31.0 `proto/gen/openapi.yaml`，避免手写时凭记忆命名。
 */

/** `ListMemoCommentsResponse`（0.31 起字段名是 memos，不再是 comments）。 */
data class ListMemoCommentsResponseDto(
    val memos: List<MemoDto> = emptyList(),
    val nextPageToken: String? = null,
)

data class ListMemoReactionsResponseDto(
    val reactions: List<ReactionDto> = emptyList(),
    val nextPageToken: String? = null,
)

data class ListMemoRelationsResponseDto(
    val relations: List<MemoRelationDto> = emptyList(),
    val nextPageToken: String? = null,
)

/** `SetMemoRelationsRequest`：name + 完整关系列表（覆盖式设置）。 */
data class SetMemoRelationsRequestDto(
    val name: String,
    val relations: List<MemoRelationDto> = emptyList(),
)

/** `UpsertMemoReactionRequest`：name 是 memo 资源名，reaction 是反应本身。 */
data class UpsertMemoReactionRequestDto(
    val name: String,
    val reaction: ReactionDto,
)

/**
 * `MemoShare`：memo 的只读分享令牌。
 * 注意 0.31 的 singular 是 memoShare，复数 memoShares。
 */
data class MemoShareDto(
    val name: String? = null,
    val createTime: String? = null,
    val expireTime: String? = null,
)

data class ListMemoSharesResponseDto(
    @SerializedName("memoShares")
    val memoShares: List<MemoShareDto> = emptyList(),
)

/** `LinkMetadata`：memo 内链接的预览元数据。 */
data class LinkMetadataDto(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
)

/** `BatchGetLinkMetadataRequest`：一次性拉取多个 url 的预览元数据。 */
data class BatchGetLinkMetadataRequestDto(
    val requests: List<LinkMetadataRequestDto> = emptyList(),
)

/** 单个链接预览请求（url 必填）。 */
data class LinkMetadataRequestDto(
    val url: String,
)

data class BatchGetLinkMetadataResponseDto(
    val linkMetadata: List<LinkMetadataDto> = emptyList(),
)

/** `ListAttachmentsResponse`。 */
data class ListAttachmentsResponseDto(
    val attachments: List<AttachmentDto> = emptyList(),
    val nextPageToken: String? = null,
)
