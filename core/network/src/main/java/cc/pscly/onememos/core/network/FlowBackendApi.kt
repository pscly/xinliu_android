package cc.pscly.onememos.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class FlowAuthData(
    val token: String,
    @SerializedName("server_url")
    val serverUrl: String,
)

/**
 * Flow Backend 的登录/注册响应。
 *
 * 兼容两种常见形态：
 * 1) 扁平：{"token":"...","server_url":"...","csrf_token":"..."}
 * 2) Envelope：{"code":200,"data":{"token":"...","server_url":"..."}}
 */
data class FlowAuthResponse(
    // 扁平形态
    val token: String? = null,
    @SerializedName("server_url")
    val serverUrl: String? = null,
    @SerializedName("csrf_token")
    val csrfToken: String? = null,

    // Envelope 形态（可选）
    val code: Int? = null,
    val data: FlowAuthData? = null,

    // 常见错误形态（可选）
    val error: String? = null,
    val message: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
)

data class FlowAuthRequest(
    val username: String,
    val password: String,
)

data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String,
    @SerializedName("new_password")
    val newPassword: String,
    @SerializedName("new_password2")
    val newPassword2: String,
)

data class ChangePasswordData(
    val ok: Boolean,
    @SerializedName("csrf_token")
    val csrfToken: String? = null,
)

/**
 * Flow Backend 的修改密码响应（同样兼容扁平与 envelope）。
 */
data class FlowChangePasswordResponse(
    // 扁平形态
    val ok: Boolean? = null,
    @SerializedName("csrf_token")
    val csrfToken: String? = null,

    // Envelope 形态（可选）
    val code: Int? = null,
    val data: ChangePasswordData? = null,

    // 常见错误形态（可选）
    val error: String? = null,
    val message: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
)

interface FlowBackendApi {
    @POST("/api/v1/auth/register")
    suspend fun register(
        @Body body: FlowAuthRequest,
    ): Response<FlowAuthResponse>

    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body body: FlowAuthRequest,
    ): Response<FlowAuthResponse>

    @POST("/api/v1/me/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body body: ChangePasswordRequest,
    ): Response<FlowChangePasswordResponse>
}
