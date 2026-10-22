package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.BatchGetLinkMetadataRequestDto
import cc.pscly.onememos.core.network.dto.BatchGetLinkMetadataResponseDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.LinkMetadataDto
import cc.pscly.onememos.core.network.dto.ListAttachmentsResponseDto
import cc.pscly.onememos.core.network.dto.ListMemoCommentsResponseDto
import cc.pscly.onememos.core.network.dto.ListMemoReactionsResponseDto
import cc.pscly.onememos.core.network.dto.ListMemoRelationsResponseDto
import cc.pscly.onememos.core.network.dto.ListMemoSharesResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.MemoShareDto
import cc.pscly.onememos.core.network.dto.ReactionDto
import cc.pscly.onememos.core.network.dto.SetMemoRelationsRequestDto
import cc.pscly.onememos.core.network.dto.UpsertMemoReactionRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 上游 memos v0.31.0 的 **memo 子资源与附件管理** 客户端接口。
 *
 * 覆盖（路径常量见 [MemosUrls]）：
 * - 评论：GET/POST `/memos/{memo}/comments`
 * - 反应：GET/POST `/memos/{memo}/reactions`、DELETE `/memos/{memo}/reactions/{reaction}`
 * - 关系：GET/PATCH `/memos/{memo}/relations`
 * - 分享：GET/POST `/memos/{memo}/shares`、DELETE `/memos/{memo}/shares/{share}`、`GET /shares/{token}/memo`
 * - 链接预览：GET/POST `/memos/-/linkMetadata`
 * - 附件：GET `/attachments`、GET/DELETE `/attachments/{attachment}`
 *
 * 现状：协议层已对齐，App 尚未接入这些能力的 UI；单独拆接口是为了不影响
 * 既有 `MemosApi` 的 Fake 实现（测试里有多份手写实现）。
 */
interface MemosMemoExtrasApi {
    @DELETE
    suspend fun deleteMemo(
        @Url url: String,
    ): EmptyDto

    @GET
    suspend fun listMemoComments(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListMemoCommentsResponseDto

    /** 0.31：新建评论的 body 就是 Memo 资源。 */
    @POST
    suspend fun createMemoComment(
        @Url url: String,
        @Body memo: MemoDto,
    ): MemoDto

    @GET
    suspend fun listMemoReactions(
        @Url url: String,
    ): ListMemoReactionsResponseDto

    @POST
    suspend fun upsertMemoReaction(
        @Url url: String,
        @Body request: UpsertMemoReactionRequestDto,
    ): ReactionDto

    @DELETE
    suspend fun deleteMemoReaction(
        @Url url: String,
    ): EmptyDto

    @GET
    suspend fun listMemoRelations(
        @Url url: String,
    ): ListMemoRelationsResponseDto

    @PATCH
    suspend fun setMemoRelations(
        @Url url: String,
        @Body request: SetMemoRelationsRequestDto,
    ): ListMemoRelationsResponseDto

    @GET
    suspend fun listMemoShares(
        @Url url: String,
    ): ListMemoSharesResponseDto

    @POST
    suspend fun createMemoShare(
        @Url url: String,
        @Body share: MemoShareDto,
    ): MemoShareDto

    @DELETE
    suspend fun deleteMemoShare(
        @Url url: String,
    ): EmptyDto

    /** 免登录读取分享内容。 */
    @GET
    suspend fun getSharedMemo(
        @Url url: String,
    ): MemoDto

    @GET
    suspend fun getLinkMetadata(
        @Url url: String,
    ): LinkMetadataDto

    @POST
    suspend fun batchGetLinkMetadata(
        @Url url: String,
        @Body request: BatchGetLinkMetadataRequestDto,
    ): BatchGetLinkMetadataResponseDto

    @GET
    suspend fun listAttachments(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListAttachmentsResponseDto

    @GET
    suspend fun getAttachment(
        @Url url: String,
    ): AttachmentDto

    @DELETE
    suspend fun deleteAttachment(
        @Url url: String,
    ): EmptyDto
}
