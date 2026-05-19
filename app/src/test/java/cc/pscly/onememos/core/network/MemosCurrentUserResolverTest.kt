package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.GetCurrentUserResponseDto
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.MemosUserDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemosCurrentUserResolverTest {
    @Test
    fun resolve_prefersAuthMe_whenLatestEndpointAvailable() =
        runBlocking {
            val api =
                RecordingMemosApi().apply {
                    authMeResponse = GetCurrentUserResponseDto(user = MemosUserDto(name = "users/alice"))
                }

            val resolver = MemosCurrentUserResolver(api)

            assertEquals("users/alice", resolver.resolve("https://example.com"))
            assertEquals(1, api.authMeCalls)
            assertEquals(0, api.currentUserCalls)
            assertEquals(0, api.authStatusCalls)
        }

    @Test
    fun resolve_withExplicitToken_usesHeaderBasedAuthMe() =
        runBlocking {
            val api =
                RecordingMemosApi().apply {
                    authMeWithAuthorizationResponse = GetCurrentUserResponseDto(user = MemosUserDto(name = "users/token-user"))
                }

            val resolver = MemosCurrentUserResolver(api)

            assertEquals("users/token-user", resolver.resolve("https://example.com", bearerToken = "abc-token"))
            assertEquals("Bearer abc-token", api.lastAuthorizationHeader)
            assertEquals(0, api.authMeCalls)
            assertEquals(1, api.authMeWithAuthorizationCalls)
        }

    @Test
    fun resolve_fallsBackToUsersMe_thenAuthStatus_whenNeeded() =
        runBlocking {
            val api =
                RecordingMemosApi().apply {
                    authMeError = IllegalStateException("404")
                    currentUserPayload =
                        JsonObject().apply {
                            addProperty("name", "users/legacy-user")
                        }
                }

            val resolver = MemosCurrentUserResolver(api)

            assertEquals("users/legacy-user", resolver.resolve("https://example.com"))
            assertEquals(1, api.authMeCalls)
            assertEquals(1, api.currentUserCalls)
            assertEquals(0, api.authStatusCalls)

            api.currentUserPayload = null
            api.currentUserError = IllegalStateException("403")
            api.authStatusPayload =
                JsonObject().apply {
                    add(
                        "user",
                        JsonObject().apply {
                            addProperty("name", "users/fallback-status")
                        },
                    )
                }

            assertEquals("users/fallback-status", resolver.resolve("https://example.com"))
            assertEquals(2, api.authMeCalls)
            assertEquals(2, api.currentUserCalls)
            assertEquals(1, api.authStatusCalls)
        }

    @Test
    fun resolve_returnsNull_whenAllEndpointsFail() =
        runBlocking {
            val api =
                RecordingMemosApi().apply {
                    authMeError = IllegalStateException("404")
                    currentUserError = IllegalStateException("404")
                    authStatusError = IllegalStateException("404")
                }

            val resolver = MemosCurrentUserResolver(api)

            assertNull(resolver.resolve("https://example.com", bearerToken = "broken"))
            assertEquals(1, api.authMeWithAuthorizationCalls)
            assertEquals(1, api.currentUserWithAuthorizationCalls)
            assertEquals(1, api.authStatusWithAuthorizationCalls)
        }

    private class RecordingMemosApi : MemosApi {
        var authMeResponse: GetCurrentUserResponseDto? = null
        var authMeWithAuthorizationResponse: GetCurrentUserResponseDto? = null
        var currentUserPayload: JsonObject? = null
        var authStatusPayload: JsonObject? = null
        var authMeError: Throwable? = null
        var currentUserError: Throwable? = null
        var authStatusError: Throwable? = null
        var lastAuthorizationHeader: String? = null

        var authMeCalls: Int = 0
        var authMeWithAuthorizationCalls: Int = 0
        var currentUserCalls: Int = 0
        var currentUserWithAuthorizationCalls: Int = 0
        var authStatusCalls: Int = 0
        var authStatusWithAuthorizationCalls: Int = 0

        override suspend fun authMe(url: String): GetCurrentUserResponseDto {
            authMeCalls += 1
            authMeError?.let { throw it }
            return authMeResponse ?: throw IllegalStateException("missing authMeResponse")
        }

        override suspend fun authMeWithAuthorization(
            url: String,
            authorization: String,
        ): GetCurrentUserResponseDto {
            authMeWithAuthorizationCalls += 1
            lastAuthorizationHeader = authorization
            authMeError?.let { throw it }
            return authMeWithAuthorizationResponse ?: throw IllegalStateException("missing authMeWithAuthorizationResponse")
        }

        override suspend fun authStatus(url: String): JsonObject {
            authStatusCalls += 1
            authStatusError?.let { throw it }
            return authStatusPayload ?: throw IllegalStateException("missing authStatusPayload")
        }

        override suspend fun authStatusWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            authStatusWithAuthorizationCalls += 1
            lastAuthorizationHeader = authorization
            authStatusError?.let { throw it }
            return authStatusPayload ?: throw IllegalStateException("missing authStatusPayload")
        }

        override suspend fun currentUser(url: String): JsonObject {
            currentUserCalls += 1
            currentUserError?.let { throw it }
            return currentUserPayload ?: throw IllegalStateException("missing currentUserPayload")
        }

        override suspend fun currentUserWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            currentUserWithAuthorizationCalls += 1
            lastAuthorizationHeader = authorization
            currentUserError?.let { throw it }
            return currentUserPayload ?: throw IllegalStateException("missing currentUserPayload")
        }

        override suspend fun listMemos(
            url: String,
            pageSize: Int,
            pageToken: String?,
            state: String?,
            orderBy: String?,
            filter: String?,
            showDeleted: Boolean?,
        ): ListMemosResponseDto = ListMemosResponseDto()

        override suspend fun getMemo(url: String): MemoDto = MemoDto()

        override suspend fun createMemo(
            url: String,
            memo: CreateMemoRequestDto,
        ): MemoDto = MemoDto()

        override suspend fun updateMemo(
            url: String,
            updateMask: String,
            memo: UpdateMemoRequestDto,
        ): MemoDto = MemoDto()

        override suspend fun setMemoAttachments(
            url: String,
            body: SetMemoAttachmentsRequestDto,
        ): EmptyDto = EmptyDto()

        override suspend fun createAttachment(
            url: String,
            attachment: CreateAttachmentRequestDto,
        ): AttachmentDto = AttachmentDto()

        override suspend fun createAttachmentRaw(
            url: String,
            body: RequestBody,
        ): AttachmentDto = AttachmentDto()
    }
}
