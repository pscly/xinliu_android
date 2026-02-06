package cc.pscly.onememos.domain.todo

/**
 * Todo tags 的本地编码：
 * - 目标：支持 SQLite 的“精确匹配”查询（避免子串误命中）。
 * - 方案：用换行包裹每个 tag，例如："\nwork\nhome\n"
 */
object TodoTagsTextCodec {
    fun encode(tags: List<String>): String {
        if (tags.isEmpty()) return "\n"
        val cleaned =
            tags.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        if (cleaned.isEmpty()) return "\n"
        return buildString {
            append('\n')
            cleaned.forEach {
                append(it)
                append('\n')
            }
        }
    }

    fun decode(tagsText: String): List<String> =
        tagsText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun needle(tag: String): String = "\n${tag.trim()}\n"
}

