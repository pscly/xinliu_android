package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.GetCurrentUserResponseDto
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import com.google.gson.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 上游 memos 的**核心**客户端接口：认证 + memo 增删改查 + 附件上传/挂载。
 *
 * 对齐版本：**v0.31.0**（`/api/v1/...` 路径体系，路径常量见 [MemosUrls]）。
 *
 * 说明：只为“当前 App 实际调用”的端点保留在本接口。
 * 评论/反应/关系/分享/链接预览/附件列表等 0.31 子资源见 [MemosMemoExtrasApi]，
 * 拆开是为了不打断既有 Fake/测试类的实现。
 */
interface MemosApi {
    @GET
    suspend fun authMe(
        @Url url: String,
    ): GetCurrentUserResponseDto

    @GET
    suspend fun authMeWithAuthorization(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): GetCurrentUserResponseDto

    @GET
    suspend fun authStatus(
        @Url url: String,
    ): JsonObject

    @GET
    suspend fun authStatusWithAuthorization(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): JsonObject

    @GET
    suspend fun currentUser(
        @Url url: String,
    ): JsonObject

    @GET
    suspend fun currentUserWithAuthorization(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): JsonObject

    @GET
    suspend fun listMemos(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null,
    ): ListMemosResponseDto

    @GET
    suspend fun getMemo(
        @Url url: String,
    ): MemoDto

    @POST
    suspend fun createMemo(
        @Url url: String,
        @Body memo: CreateMemoRequestDto,
    ): MemoDto

    @PATCH
    suspend fun updateMemo(
        @Url url: String,
        @Query("updateMask") updateMask: String,
        @Body memo: UpdateMemoRequestDto,
    ): MemoDto

    @PATCH
    suspend fun setMemoAttachments(
        @Url url: String,
        @Body body: SetMemoAttachmentsRequestDto,
    ): EmptyDto

    @POST
    suspend fun createAttachment(
        @Url url: String,
        @Body attachment: CreateAttachmentRequestDto,
    ): AttachmentDto

    @POST
    suspend fun createAttachmentRaw(
        @Url url: String,
        @Body body: RequestBody,
    ): AttachmentDto
}
