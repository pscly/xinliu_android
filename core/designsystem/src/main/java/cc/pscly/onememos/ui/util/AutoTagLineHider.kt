package cc.pscly.onememos.ui.util

/**
 * 隐藏“自动标签元数据行”（例如 n8n/LLM 写入的 `__Atags ...`）。
 *
 * 约定：
 * - 以“行尾关键字”判断（忽略行尾空白；关键字前允许空白/冒号）
 * - 支持多个关键字（逗号/空格/换行分隔）
 * - 隐藏时只影响显示；保存时可把隐藏行拼回去，保证内容不丢
 */
object AutoTagLineHider {
    data class SplitResult(
        val visibleText: String,
        val hiddenLines: List<String>,
    )

    fun parseKeywords(raw: String?): List<String> {
        val src = raw.orEmpty()
        val tokens =
            src
                .replace('，', ',')
                .replace('；', ';')
                .split(Regex("[,;\\s\\n\\r]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        return (if (tokens.isEmpty()) listOf("__Atags") else tokens).distinct()
    }

    fun split(text: String, keywords: List<String>): SplitResult {
        val keys = keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return SplitResult(visibleText = text, hiddenLines = emptyList())

        val normalized = text.replace("\r\n", "\n")
        // 关键：保留末尾空行（用户需要能输入换行/空行）。
        // Kotlin 的 split(limit) 要求非负，因此用一个足够大的 limit 来避免丢掉 trailing empty。
        val lines = normalized.split("\n", ignoreCase = false, limit = Int.MAX_VALUE)

        val visible = ArrayList<String>(lines.size)
        val hidden = ArrayList<String>(2)

        for (line in lines) {
            val trimmedEnd = line.trimEnd()
            val hit =
                keys.any { key ->
                    if (!trimmedEnd.endsWith(key)) return@any false
                    if (trimmedEnd.length == key.length) return@any true
                    val prev = trimmedEnd[trimmedEnd.length - key.length - 1]
                    prev.isWhitespace() || prev == ':' || prev == '：'
                }
            if (hit) hidden.add(line) else visible.add(line)
        }

        return SplitResult(
            visibleText = visible.joinToString("\n"),
            hiddenLines = hidden,
        )
    }

    fun hide(text: String, keywords: List<String>): String = split(text, keywords).visibleText

    /**
     * 更轻量的隐藏实现：按行扫描，避免 split 带来的大分配。
     *
     * 适用场景：首页列表预览、快速记录等“频繁触发/对性能敏感”的地方。
     * 注意：语义需与 [split] 保持一致（行尾关键字判断、忽略行尾空白、关键字前允许空白/冒号）。
     */
    fun hideFast(text: String, keywords: List<String>): String {
        val keys = keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return text
        if (text.isEmpty()) return text

        val sb = StringBuilder(text.length)
        var firstVisibleLine = true

        var i = 0
        val len = text.length
        while (i <= len) {
            val lineStart = i
            var lineEnd = lineStart
            while (lineEnd < len && text[lineEnd] != '\n') {
                lineEnd++
            }

            var endExclusive = lineEnd
            if (endExclusive > lineStart && text[endExclusive - 1] == '\r') {
                endExclusive--
            }

            val hit = lineEndsWithAnyKeyword(text, lineStart, endExclusive, keys)
            if (!hit) {
                if (!firstVisibleLine) sb.append('\n') else firstVisibleLine = false
                if (endExclusive > lineStart) {
                    sb.append(text, lineStart, endExclusive)
                }
            }

            if (lineEnd >= len) break
            i = lineEnd + 1
        }

        return sb.toString()
    }

    private fun lineEndsWithAnyKeyword(
        text: String,
        start: Int,
        endExclusive: Int,
        keys: List<String>,
    ): Boolean {
        if (start >= endExclusive) return false

        var trimmedEnd = endExclusive
        while (trimmedEnd > start && text[trimmedEnd - 1].isWhitespace()) {
            trimmedEnd--
        }
        if (trimmedEnd <= start) return false

        for (key in keys) {
            if (trimmedEnd - start < key.length) continue
            val keyStart = trimmedEnd - key.length
            if (!text.regionMatches(keyStart, key, 0, key.length, ignoreCase = false)) continue
            if (keyStart == start) return true
            val prev = text[keyStart - 1]
            if (prev.isWhitespace() || prev == ':' || prev == '：') return true
        }
        return false
    }

    fun merge(visibleText: String, hiddenLines: List<String>): String {
        if (hiddenLines.isEmpty()) return visibleText
        val base = visibleText
        val tail = hiddenLines.joinToString("\n")
        if (base.isBlank()) return tail
        // 保留用户输入的末尾换行/空行，不强行 trim。
        return if (base.endsWith("\n")) base + tail else base + "\n" + tail
    }
}
