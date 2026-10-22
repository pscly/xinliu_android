package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.ListMemoViewsResponseDto
import cc.pscly.onememos.core.network.dto.MemoViewDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 上游 memos v0.31.0 `UserService` 里的 MemoView（可复用列表视图）客户端定义。
 *
 * 背景：0.31 把旧的 Shortcut 迁移成了 View，集成方必须改用
 * `/api/v1/users/{user}/views` 这组接口（旧的 Shortcut 接口已下线）。
 *
 * 说明：当前 APK 未接入 View UI，先对齐协议层；[MemoViewDto.filter] 可直接传给
 * ListMemos 的 filter 参数复用同一份筛选表达式。
 */
interface MemosViewApi {
    @GET
    suspend fun listMemoViews(
        @Url url: String,
    ): ListMemoViewsResponseDto

    @POST
    suspend fun createMemoView(
        @Url url: String,
        @Body view: MemoViewDto,
    ): MemoViewDto

    @GET
    suspend fun getMemoView(
        @Url url: String,
    ): MemoViewDto

    @PATCH
    suspend fun updateMemoView(
        @Url url: String,
        @Query("updateMask") updateMask: String,
        @Body view: MemoViewDto,
    ): MemoViewDto

    @DELETE
    suspend fun deleteMemoView(
        @Url url: String,
    ): EmptyDto
}
