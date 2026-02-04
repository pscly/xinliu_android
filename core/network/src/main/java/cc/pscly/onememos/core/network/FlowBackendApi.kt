package cc.pscly.onememos.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class FlowEnvelope<T>(
    val code: Int,
    val data: T? = null,
)

data class FlowAuthData(
    val token: String,
    @SerializedName("server_url")
    val serverUrl: String,
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

interface FlowBackendApi {
    @POST("/api/v1/auth/register")
    suspend fun register(
        @Body body: FlowAuthRequest,
    ): Response<FlowEnvelope<FlowAuthData>>

    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body body: FlowAuthRequest,
    ): Response<FlowEnvelope<FlowAuthData>>

    @POST("/api/v1/me/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body body: ChangePasswordRequest,
    ): Response<FlowEnvelope<ChangePasswordData>>
}
