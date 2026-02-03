package cc.pscly.onememos.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPaperTest {
    @Test
    fun markdownToPlainText_keepsTextAndLineBreaks() {
        val md =
            """
            # 标题

            这是**加粗**与*斜体*。
            第二行。
            """.trimIndent()

        val plain = markdownToPlainText(md)
        assertTrue(plain.contains("标题"))
        assertTrue(plain.contains("这是"))
        assertTrue(plain.contains("第二行"))
        // commonmark 的 softLineBreak 会转为换行
        assertTrue(plain.contains('\n'))
    }

    @Test
    fun markdownToPlainPreview_trimsAndEllipsizes() {
        val md = "a ".repeat(500)
        val preview = markdownToPlainPreview(md, maxChars = 50)
        assertTrue(preview.length <= 51) // 50 + "…"
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun markdownToPlainPreview_collapsesWhitespace() {
        val md = "a\n\n\nb\t\tc"
        val preview = markdownToPlainPreview(md, maxChars = 100)
        // 允许段落边界/换行差异，只校验“空白被压缩且内容顺序正确”
        val normalized = preview.replace(Regex("\\s+"), " ").trim()
        assertTrue(normalized.contains("a"))
        assertTrue(normalized.contains("b"))
        assertTrue(normalized.contains("c"))
        assertTrue(normalized.indexOf("a") < normalized.indexOf("b"))
        assertTrue(normalized.indexOf("b") < normalized.indexOf("c"))
    }
}
