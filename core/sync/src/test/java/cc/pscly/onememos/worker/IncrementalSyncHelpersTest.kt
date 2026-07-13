package cc.pscly.onememos.worker

import cc.pscly.onememos.core.network.MemosApi
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
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class IncrementalSyncHelpersTest {
    @Test
    fun recentPage_usesCurrentMemosOrderFields() =
        runBlocking {
            val expected = ListMemosResponseDto(nextPageToken = "next")
            val api = RecordingMemosApi(responses = ArrayDeque(listOf(expected)))

            val result =
                listRecentMemosPage(
                    memosApi = api,
                    url = "https://example.com/api/v1/memos",
                    pageToken = null,
                    orderBy = RECENT_MEMOS_ORDER_BY,
                )

            assertSame(expected, result.response)
            assertEquals(RECENT_MEMOS_ORDER_BY, result.orderBy)
            assertEquals(listOf(50), api.pageSizeCalls)
            assertEquals(listOf("NORMAL"), api.stateCalls)
            assertEquals(listOf("pinned desc, create_time desc"), api.orderByCalls)
        }

    @Test
    fun recentPage_fallsBackWithoutOrder_whenServerRejectsOrderBy_andKeepsFallbackForNextPage() =
        runBlocking {
            val firstPage = ListMemosResponseDto(nextPageToken = "page-2")
            val secondPage = ListMemosResponseDto(nextPageToken = null)
            val api =
                RecordingMemosApi(
                    responses = ArrayDeque(listOf(firstPage, secondPage)),
                    firstOrderedError =
                        httpException(
                            code = 400,
                            json =
                                """{"code":3,"message":"invalid order_by: unsupported order field: create_time","details":[]}""",
                        ),
                )

            val firstResult =
                listRecentMemosPage(
                    memosApi = api,
                    url = "https://example.com/api/v1/memos",
                    pageToken = null,
                    orderBy = RECENT_MEMOS_ORDER_BY,
                )
            val secondResult =
                listRecentMemosPage(
                    memosApi = api,
                    url = "https://example.com/api/v1/memos",
                    pageToken = firstResult.response.nextPageToken,
                    orderBy = firstResult.orderBy,
                )

            assertSame(firstPage, firstResult.response)
            assertNull(firstResult.orderBy)
            assertSame(secondPage, secondResult.response)
            assertNull(secondResult.orderBy)
            assertEquals(listOf(RECENT_MEMOS_ORDER_BY, null, null), api.orderByCalls)
            assertEquals(listOf(null, null, "page-2"), api.pageTokenCalls)
        }

    @Test
    fun recentPage_doesNotHideUnrelatedHttp400() =
        runBlocking {
            val expected =
                httpException(
                    code = 400,
                    json = """{"code":3,"message":"invalid state","details":[]}""",
                )
            val api = RecordingMemosApi(responses = ArrayDeque(), firstOrderedError = expected)

            try {
                listRecentMemosPage(
                    memosApi = api,
                    url = "https://example.com/api/v1/memos",
                    pageToken = null,
                    orderBy = RECENT_MEMOS_ORDER_BY,
                )
                fail("非排序参数导致的 HTTP 400 必须继续向上抛出")
            } catch (actual: HttpException) {
                assertSame(expected, actual)
            }

            assertEquals(listOf(RECENT_MEMOS_ORDER_BY), api.orderByCalls)
        }

    private class RecordingMemosApi(
        private val responses: ArrayDeque<ListMemosResponseDto>,
        private var firstOrderedError: HttpException? = null,
    ) : MemosApi {
        val orderByCalls = mutableListOf<String?>()
        val pageTokenCalls = mutableListOf<String?>()
        val pageSizeCalls = mutableListOf<Int>()
        val stateCalls = mutableListOf<String?>()

        override suspend fun listMemos(
            url: String,
            pageSize: Int,
            pageToken: String?,
            state: String?,
            orderBy: String?,
            filter: String?,
            showDeleted: Boolean?,
        ): ListMemosResponseDto {
            pageSizeCalls += pageSize
            stateCalls += state
            orderByCalls += orderBy
            pageTokenCalls += pageToken
            if (orderBy != null) {
                firstOrderedError?.let {
                    firstOrderedError = null
                    throw it
                }
            }
            return responses.removeFirst()
        }

        override suspend fun authMe(url: String): GetCurrentUserResponseDto = unused()

        override suspend fun authMeWithAuthorization(
            url: String,
            authorization: String,
        ): GetCurrentUserResponseDto = unused()

        override suspend fun authStatus(url: String): JsonObject = unused()

        override suspend fun authStatusWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject = unused()

        override suspend fun currentUser(url: String): JsonObject = unused()

        override suspend fun currentUserWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject = unused()

        override suspend fun getMemo(url: String): MemoDto = unused()

        override suspend fun createMemo(
            url: String,
            memo: CreateMemoRequestDto,
        ): MemoDto = unused()

        override suspend fun updateMemo(
            url: String,
            updateMask: String,
            memo: UpdateMemoRequestDto,
        ): MemoDto = unused()

        override suspend fun setMemoAttachments(
            url: String,
            body: SetMemoAttachmentsRequestDto,
        ): EmptyDto = unused()

        override suspend fun createAttachment(
            url: String,
            attachment: CreateAttachmentRequestDto,
        ): AttachmentDto = unused()

        override suspend fun createAttachmentRaw(
            url: String,
            body: RequestBody,
        ): AttachmentDto = unused()

        private fun <T> unused(): T = throw UnsupportedOperationException("测试不会调用此接口")
    }

    private fun httpException(
        code: Int,
        json: String,
    ): HttpException {
        val body = json.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}
