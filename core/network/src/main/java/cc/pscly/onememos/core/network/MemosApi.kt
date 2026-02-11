package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import com.google.gson.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface MemosApi {
    @GET
    suspend fun authStatus(
        @Url url: String,
    ): JsonObject

    @GET
    suspend fun currentUser(
        @Url url: String,
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
