package cc.pscly.onememos.ui.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 基于光标位置的简单标签联想：
 * - 当光标位于 "#xxx" 的 xxx 末尾时，返回 xxx 作为前缀
 * - 只考虑当前光标前的最近一个 '#'
 */
object TagCompletion {
    private val tagCharRegex = Regex("[\\p{L}\\p{N}_-]")

    data class TagPrefix(
        val prefix: String,
        val startInclusive: Int, // '#'
        val endExclusive: Int, // 光标位置
    )

    fun findTagPrefix(value: TextFieldValue): TagPrefix? {
        val text = value.text
        val cursor = value.selection.start
        if (cursor <= 0 || cursor > text.length) return null

        // 从光标向左找最近的 '#'
        var i = cursor - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == '#') {
                val prefix = text.substring(i + 1, cursor)
                // "# " 这种不是标签输入
                if (prefix.isNotEmpty() && !prefix.all { it.toString().matches(tagCharRegex) }) return null
                if (prefix.isEmpty()) {
                    return TagPrefix(prefix = "", startInclusive = i, endExclusive = cursor)
                }
                // prefix 全部是允许字符才联想
                if (prefix.all { it.toString().matches(tagCharRegex) }) {
                    return TagPrefix(prefix = prefix, startInclusive = i, endExclusive = cursor)
                }
                return null
            }

            // 遇到空白/换行/标点：停止回溯（避免跨词匹配）
            if (ch.isWhitespace() || ch == '\n' || ch == '\t') break
            i--
        }
        return null
    }

    fun applySuggestion(
        value: TextFieldValue,
        tag: String,
        prefix: TagPrefix,
    ): TextFieldValue {
        val text = value.text
        val before = text.substring(0, prefix.startInclusive)
        val after = text.substring(prefix.endExclusive)

        val insert = "#$tag"
        val needsSpaceAfter =
            after.firstOrNull()?.let { !it.isWhitespace() } ?: false

        val nextText =
            buildString {
                append(before)
                append(insert)
                if (needsSpaceAfter) append(' ')
                append(after)
            }

        val newCursor = (before.length + insert.length + if (needsSpaceAfter) 1 else 0)
        return TextFieldValue(
            text = nextText,
            selection = TextRange(newCursor),
        )
    }
}
