package cc.pscly.onememos.worker

import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.domain.model.MemoServerState
import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * 增量拉取的排序：**按更新时间倒序**。
 *
 * 为什么不是 `create_time desc`：
 * - 增量同步的目标是把“远端最近的改动”同步下来，包括**对老笔记的编辑**；
 * - 按创建时间排序时，被编辑的老笔记排得很后，翻页也翻不到；
 * - 按更新时间排序后，第 1 页就是“最近变化”的记录，因此“本页无变化 ⇒ 更旧的页也无变化”
 *   这一提前停止条件才是成立的（见 MemosSyncWorker.refreshFromServer）。
 *
 * 服务端（memos 0.31）实测支持 `orderBy=update_time desc`。
 * 若老服务端拒绝该写法，[listRecentMemosPage] 会回退为默认排序，此时调用方不得提前停止翻页。
 */
internal const val RECENT_MEMOS_ORDER_BY: String = "update_time desc"

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
