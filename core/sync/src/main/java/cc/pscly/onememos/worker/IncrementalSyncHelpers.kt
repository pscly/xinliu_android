package cc.pscly.onememos.worker

import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.domain.model.MemoServerState
import com.google.gson.JsonParser
import retrofit2.HttpException

internal const val RECENT_MEMOS_ORDER_BY: String = "pinned desc, create_time desc"

internal data class RecentMemosPage(
    val response: ListMemosResponseDto,
    val orderBy: String?,
)

internal suspend fun listRecentMemosPage(
    memosApi: MemosApi,
    url: String,
    pageToken: String?,
    orderBy: String?,
): RecentMemosPage {
    return try {
        RecentMemosPage(
            response = memosApi.listRecentMemos(url = url, pageToken = pageToken, orderBy = orderBy),
            orderBy = orderBy,
        )
    } catch (error: HttpException) {
        if (orderBy == null || !error.isInvalidOrderBy()) throw error

        RecentMemosPage(
            response = memosApi.listRecentMemos(url = url, pageToken = pageToken, orderBy = null),
            orderBy = null,
        )
    }
}

private suspend fun MemosApi.listRecentMemos(
    url: String,
    pageToken: String?,
    orderBy: String?,
): ListMemosResponseDto =
    listMemos(
        url = url,
        pageSize = 50,
        pageToken = pageToken,
        state = MemoServerState.NORMAL.name,
        orderBy = orderBy,
    )

private fun HttpException.isInvalidOrderBy(): Boolean {
    if (code() != 400) return false

    val message =
        runCatching {
            val raw = response()?.errorBody()?.string().orEmpty()
            JsonParser.parseString(raw).asJsonObject.get("message")?.asString
        }.getOrNull()

    return message?.startsWith("invalid order_by", ignoreCase = true) == true
}
