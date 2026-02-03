package cc.pscly.onememos.core.network.dto

/**
 * CreateMemo 的 HTTP body 是 Memo（而不是包一层 request）。
 * 只保留创建必需字段：state/content/visibility。
 */
data class CreateMemoRequestDto(
    val state: String,
    val content: String,
    val visibility: String,
)
