package cc.pscly.onememos.core.network.dto

/**
 * UpdateMemo 的 HTTP body 是 Memo（而不是包一层 request），updateMask 走 query 参数。
 * 这里只保留我们会更新的字段；0.31 起补上 location。
 */
data class UpdateMemoRequestDto(
    val name: String,
    val state: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    val pinned: Boolean? = null,
    val location: MemoLocationDto? = null,
)
