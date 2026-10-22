package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.ListSpaceInvitationsResponseDto
import cc.pscly.onememos.core.network.dto.ListSpaceMembersResponseDto
import cc.pscly.onememos.core.network.dto.ListSpacesResponseDto
import cc.pscly.onememos.core.network.dto.ListUserSpaceInvitationsResponseDto
import cc.pscly.onememos.core.network.dto.SpaceDto
import cc.pscly.onememos.core.network.dto.SpaceInvitationDto
import cc.pscly.onememos.core.network.dto.SpaceMemberDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 上游 memos v0.31.0 `SpaceService`（协作空间）的客户端定义。
 *
 * 说明：
 * - 路径常量集中在 [MemosUrls]，本接口只描述方法与请求体；
 * - 当前 APK 还没有 Space 相关 UI，这里先对齐协议层，便于后续接入；
 * - 创建/更新沿用上游约定：请求体直接是资源本身（Space/SpaceMember/SpaceInvitation）。
 */
interface MemosSpaceApi {
    @GET
    suspend fun listSpaces(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListSpacesResponseDto

    @POST
    suspend fun createSpace(
        @Url url: String,
        @Body space: SpaceDto,
    ): SpaceDto

    @GET
    suspend fun getSpace(
        @Url url: String,
    ): SpaceDto

    @PATCH
    suspend fun updateSpace(
        @Url url: String,
        @Query("updateMask") updateMask: String,
        @Body space: SpaceDto,
    ): SpaceDto

    @DELETE
    suspend fun deleteSpace(
        @Url url: String,
    ): EmptyDto

    @GET
    suspend fun listSpaceMembers(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListSpaceMembersResponseDto

    @GET
    suspend fun getSpaceMember(
        @Url url: String,
    ): SpaceMemberDto

    @PATCH
    suspend fun updateSpaceMember(
        @Url url: String,
        @Query("updateMask") updateMask: String,
        @Body member: SpaceMemberDto,
    ): SpaceMemberDto

    @DELETE
    suspend fun deleteSpaceMember(
        @Url url: String,
    ): EmptyDto

    @GET
    suspend fun listSpaceInvitations(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListSpaceInvitationsResponseDto

    @POST
    suspend fun createSpaceInvitation(
        @Url url: String,
        @Body invitation: SpaceInvitationDto,
    ): SpaceInvitationDto

    @GET
    suspend fun getSpaceInvitation(
        @Url url: String,
    ): SpaceInvitationDto

    @DELETE
    suspend fun deleteSpaceInvitation(
        @Url url: String,
    ): EmptyDto

    @POST
    suspend fun acceptSpaceInvitation(
        @Url url: String,
    ): EmptyDto

    @POST
    suspend fun declineSpaceInvitation(
        @Url url: String,
    ): EmptyDto

    @GET
    suspend fun listUserSpaceInvitations(
        @Url url: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListUserSpaceInvitationsResponseDto
}
