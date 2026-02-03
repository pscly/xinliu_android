package cc.pscly.onememos.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTagLineHiderTest {
    @Test
    fun parseKeywords_defaultsToAtags() {
        assertEquals(listOf("__Atags"), AutoTagLineHider.parseKeywords(null))
        assertEquals(listOf("__Atags"), AutoTagLineHider.parseKeywords(""))
        assertEquals(listOf("__Atags"), AutoTagLineHider.parseKeywords("   \n\n"))
    }

    @Test
    fun parseKeywords_splitsAndDedups() {
        val raw = "__Atags, foo；bar  foo\n__Atags"
        val keys = AutoTagLineHider.parseKeywords(raw)
        assertEquals(listOf("__Atags", "foo", "bar"), keys)
    }

    @Test
    fun split_hidesLinesBySuffixKeyword() {
        val text = "第一行\n标签: __Atags\n第三行"
        val split = AutoTagLineHider.split(text, listOf("__Atags"))
        assertEquals("第一行\n第三行", split.visibleText)
        assertEquals(listOf("标签: __Atags"), split.hiddenLines)
    }

    @Test
    fun split_doesNotHideWhenKeywordNotAtEnd() {
        val text = "__Atags 这一行不是元数据\n第二行"
        val split = AutoTagLineHider.split(text, listOf("__Atags"))
        assertEquals(text, split.visibleText)
        assertTrue(split.hiddenLines.isEmpty())
    }

    @Test
    fun split_ignoresTrailingSpaces() {
        val text = "a\n标签: __Atags   \n\n"
        val split = AutoTagLineHider.split(text, listOf("__Atags"))
        // 关键：保留末尾空行
        assertEquals("a\n\n", split.visibleText)
        assertEquals(listOf("标签: __Atags   "), split.hiddenLines)
    }

    @Test
    fun merge_keepsUserNewlinesAndAppendsHiddenLines() {
        val visible = "a\n\n"
        val hidden = listOf("标签: __Atags")
        val merged = AutoTagLineHider.merge(visible, hidden)
        assertEquals("a\n\n标签: __Atags", merged)
    }

    @Test
    fun hideFast_matches_hide_for_common_cases() {
        val keys = listOf("__Atags")

        val t1 = "第一行\n标签: __Atags\n第三行"
        assertEquals(AutoTagLineHider.hide(t1, keys), AutoTagLineHider.hideFast(t1, keys))

        val t2 = "__Atags 这一行不是元数据\n第二行"
        assertEquals(AutoTagLineHider.hide(t2, keys), AutoTagLineHider.hideFast(t2, keys))

        // 关键：保留末尾空行
        val t3 = "a\n标签: __Atags   \n\n"
        assertEquals(AutoTagLineHider.hide(t3, keys), AutoTagLineHider.hideFast(t3, keys))

        // 兼容 CRLF
        val t4 = "a\r\n标签: __Atags\r\nb\r\n"
        assertEquals(AutoTagLineHider.hide(t4, keys), AutoTagLineHider.hideFast(t4, keys))
    }
}
