package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.GetCurrentUserResponseDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemosCurrentUserResolver @Inject constructor(
    private val memosApi: MemosApi,
) {
    suspend fun resolve(
        serverUrl: String,
        bearerToken: String? = null,
    ): String? {
        val serverBase = MemosUrls.normalizeServerBase(serverUrl) ?: return null
        val authorization = bearerToken.toBearerAuthorization()

        if (authorization != null) {
            extractFromAuthMeWithAuthorization(serverBase, authorization)?.let { return it }
            extractFromCurrentUserWithAuthorization(serverBase, authorization)?.let { return it }
            extractFromAuthStatusWithAuthorization(serverBase, authorization)?.let { return it }
            return null
        }

        extractFromAuthMe(serverBase)?.let { return it }
        extractFromCurrentUser(serverBase)?.let { return it }
        extractFromAuthStatus(serverBase)?.let { return it }
        return null
    }

    private suspend fun extractFromAuthMe(serverBase: String): String? =
        runCatching {
            memosApi.authMe(MemosUrls.authMe(serverBase))
        }.getOrNull().extractCreatorName()

    private suspend fun extractFromAuthMeWithAuthorization(
        serverBase: String,
        authorization: String,
    ): String? =
        runCatching {
            memosApi.authMeWithAuthorization(MemosUrls.authMe(serverBase), authorization)
        }.getOrNull().extractCreatorName()

    private suspend fun extractFromCurrentUser(serverBase: String): String? =
        runCatching {
            memosApi.currentUser(MemosUrls.currentUser(serverBase))
        }.getOrNull().extractJsonCreatorName()

    private suspend fun extractFromCurrentUserWithAuthorization(
        serverBase: String,
        authorization: String,
    ): String? =
        runCatching {
            memosApi.currentUserWithAuthorization(MemosUrls.currentUser(serverBase), authorization)
        }.getOrNull().extractJsonCreatorName()

    private suspend fun extractFromAuthStatus(serverBase: String): String? =
        runCatching {
            memosApi.authStatus(MemosUrls.authStatus(serverBase))
        }.getOrNull().extractJsonCreatorName()

    private suspend fun extractFromAuthStatusWithAuthorization(
        serverBase: String,
        authorization: String,
    ): String? =
        runCatching {
            memosApi.authStatusWithAuthorization(MemosUrls.authStatus(serverBase), authorization)
        }.getOrNull().extractJsonCreatorName()

    private fun GetCurrentUserResponseDto?.extractCreatorName(): String? =
        MemosIdentityParser.extractCreatorName(this)

    private fun com.google.gson.JsonObject?.extractJsonCreatorName(): String? =
        MemosIdentityParser.extractCreatorName(this)

    private fun String?.toBearerAuthorization(): String? {
        val token = this?.trim().orEmpty()
        if (token.isBlank()) return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }
}
