package cc.pscly.onememos.ui.filter

enum class TagMatchMode {
    OR,
    AND,
}

data class MemoFilter(
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val tagMatchMode: TagMatchMode = TagMatchMode.OR,
)

