package cc.pscly.onememos.core.network.dto

/**
 * CreateMemo 的 HTTP body 是 Memo（而不是包一层 request）。
 *
 * 必填：state/content/visibility。
 * 可选：location（0.31 新增，用于地图视图）、pinned、parent（评论类 memo）。
 */
data class CreateMemoRequestDto(
    val state: String,
    val content: String,
    val visibility: String,
    val pinned: Boolean? = null,
    val parent: String? = null,
    val location: MemoLocationDto? = null,
)
