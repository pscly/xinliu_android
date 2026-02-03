package cc.pscly.onememos.domain.derived

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoDerivedFieldsTest {
    @Test
    fun tagsTextCodec_encode_decode_roundtrip() {
        val tags = listOf("work", "life")
        val encoded = TagsTextCodec.encode(tags)
        assertEquals("\nwork\nlife\n", encoded)
        assertEquals(tags, TagsTextCodec.decode(encoded))
    }

    @Test
    fun tagsTextCodec_encode_dedupes_and_trims() {
        val tags = listOf(" work ", "life", "work", "", "  ")
        val encoded = TagsTextCodec.encode(tags)
        assertEquals("\nwork\nlife\n", encoded)
        assertEquals(listOf("work", "life"), TagsTextCodec.decode(encoded))
    }

    @Test
    fun tagsTextCodec_decode_handles_blank() {
        assertEquals(emptyList<String>(), TagsTextCodec.decode(""))
        assertEquals(emptyList<String>(), TagsTextCodec.decode("\n\n"))
    }

    @Test
    fun markdownDeriver_plainPreview_trims_and_ellipsizes() {
        val md = "a ".repeat(500)
        val preview = MarkdownDeriver.plainPreview(md, maxChars = 50)
        assertTrue(preview.length <= 51) // 50 + "…"
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun markdownDeriver_plainPreview_collapses_whitespace() {
        val md = "a\n\n\nb\t\tc"
        val preview = MarkdownDeriver.plainPreview(md, maxChars = 100)
        val normalized = preview.replace(Regex("\\s+"), " ").trim()
        assertTrue(normalized.contains("a"))
        assertTrue(normalized.contains("b"))
        assertTrue(normalized.contains("c"))
        assertTrue(normalized.indexOf("a") < normalized.indexOf("b"))
        assertTrue(normalized.indexOf("b") < normalized.indexOf("c"))
    }

    @Test
    fun markdownDeriver_plainPreviewSkippingLinesEndingWithKeywords_hides_metadata_lines() {
        val md = "第一行\n标签: __Atags\n第三行"
        val preview =
            MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                markdown = md,
                keywords = listOf("__Atags"),
                maxChars = 200,
            )
        assertEquals("第一行 第三行", preview)
    }

    @Test
    fun markdownDeriver_plainPreviewSkippingLinesEndingWithKeywords_does_not_hide_non_matching_lines() {
        val md = "__Atags 这一行不是元数据\n第二行"
        val preview =
            MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                markdown = md,
                keywords = listOf("__Atags"),
                maxChars = 200,
            )
        assertEquals("__Atags 这一行不是元数据 第二行", preview)
    }

    @Test
    fun markdownDeriver_plainPreviewSkippingLinesEndingWithKeywords_returns_blank_when_all_lines_hidden() {
        val md = "标签: __Atags\n标签: __Atags   "
        val preview =
            MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                markdown = md,
                keywords = listOf("__Atags"),
                maxChars = 200,
            )
        assertEquals("", preview)
    }
}
