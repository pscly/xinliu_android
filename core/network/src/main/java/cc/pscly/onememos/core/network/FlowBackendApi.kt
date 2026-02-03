package cc.pscly.onememos.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
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

interface FlowBackendApi {
    @POST("/api/v1/auth/register")
    suspend fun register(
        @Body body: FlowAuthRequest,
    ): Response<FlowEnvelope<FlowAuthData>>

    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body body: FlowAuthRequest,
    ): Response<FlowEnvelope<FlowAuthData>>
}
