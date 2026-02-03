package cc.pscly.onememos.domain.tag

/**
 * 标签提取：
 * - memos 习惯用 #tag 作为标签（可中文/英文/数字/下划线/短横线）。
 * - 注意："# 标题" 这种 Markdown 标题也以 # 开头，但它不是标签；这里要求 # 后紧跟“可用字符”才算标签。
 */
object TagExtractor {
    // 说明：memos 的标签经常写成 “今天#心情”，也可能是 “hello #work”。
    // 这里用“ASCII 单词字符边界”来避免把 "abc#tag" 识别成标签，但允许中文等非 ASCII 场景。
    private val tagRegex = Regex("(?<![A-Za-z0-9_])#([\\p{L}\\p{N}_-]{1,32})")

    fun extractAll(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val result = LinkedHashSet<String>()
        tagRegex.findAll(content).forEach { m ->
            val tag = m.groupValues.getOrNull(1).orEmpty()
            if (tag.isNotBlank()) result += tag
        }
        return result.toList()
    }

    fun stripTagTokens(text: String): String {
        if (text.isBlank()) return ""
        return tagRegex
            .replace(text, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
