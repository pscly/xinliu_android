package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

data class GetCurrentUserResponseDto(
    val user: MemosUserDto? = null,
    val code: Int? = null,
    val data: GetCurrentUserDataDto? = null,
    val error: String? = null,
    val message: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
)

data class GetCurrentUserDataDto(
    val user: MemosUserDto? = null,
)
