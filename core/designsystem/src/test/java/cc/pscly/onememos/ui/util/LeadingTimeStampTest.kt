package cc.pscly.onememos.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动时间戳“识别 + 浅色显示”的回归测试。
 *
 * 需求背景：时间戳是正文的一部分（可保存、可手动删除），但在输入框里要以浅色/半透明显示，
 * 不能和用户正文抢注意力；普通 Markdown 引用（`> 正文`）不能被误判成时间戳。
 */
class LeadingTimeStampTest {
    @Test
    fun fullDateTimeStamp_isDetected() {
        val text = "> 2026-10-27 10:56:37\n\n正文"
        assertEquals(21, LeadingTimeStamp.lineEndOrNull(text))
        assertFalse(LeadingTimeStamp.bodyIsBlank(text))
    }

    @Test
    fun timeOnlyStamp_isDetected() {
        val text = "> 10:56:37\n\n正文"
        assertEquals(10, LeadingTimeStamp.lineEndOrNull(text))
    }

    @Test
    fun chineseDateTimeStamp_isDetected() {
        // 自定义格式可能带中文单位
        assertTrue(LeadingTimeStamp.lineEndOrNull("> 2026年10月27日 10:56") != null)
    }

    @Test
    fun normalQuote_isNotDetected() {
        assertNull(LeadingTimeStamp.lineEndOrNull("> 这是一段普通引用"))
        assertNull(LeadingTimeStamp.lineEndOrNull("> 引用 abc 1234"))
    }

    @Test
    fun stampNotAtStart_isNotDetected() {
        assertNull(LeadingTimeStamp.lineEndOrNull("正文\n> 2026-10-27 10:56:37"))
    }

    @Test
    fun plainText_isNotDetected() {
        assertNull(LeadingTimeStamp.lineEndOrNull("2026-10-27 10:56:37"))
        assertNull(LeadingTimeStamp.lineEndOrNull(""))
    }

    @Test
    fun bodyStart_skipsStampAndBlankLines() {
        val text = "> 2026-10-27 10:56:37\n\n你好"
        val start = LeadingTimeStamp.bodyStart(text)
        assertEquals("你好", text.substring(start))
    }

    @Test
    fun bodyIsBlank_trueWhenOnlyStamp() {
        assertTrue(LeadingTimeStamp.bodyIsBlank("> 2026-10-27 10:56:37\n\n"))
        assertTrue(LeadingTimeStamp.bodyIsBlank("> 2026-10-27 10:56:37"))
        assertTrue(LeadingTimeStamp.bodyIsBlank("   "))
        assertFalse(LeadingTimeStamp.bodyIsBlank("> 2026-10-27 10:56:37\n\n有内容"))
    }

    @Test
    fun bodyStartLineOffset_countsStampAndBlankLine() {
        assertEquals(2, LeadingTimeStamp.bodyStartLineOffset("> 2026-10-27 10:56:37\n\nbody"))
        assertEquals(0, LeadingTimeStamp.bodyStartLineOffset("没有时间戳"))
    }

    @Test
    fun muteTransformation_keepsTextAndCursorMapping() {
        val text = "> 2026-10-27 10:56:37\n\n正文"
        val transformation = TimestampMuteTransformation(androidx.compose.ui.graphics.Color.Red)
        val transformed = transformation.filter(AnnotatedString(text))

        // 只加样式、不改字符：文本不变，偏移映射为 Identity（光标不会错位）
        assertEquals(text, transformed.text.text)
        assertEquals(OffsetMapping.Identity, transformed.offsetMapping)
        // 时间戳行被染色
        assertTrue(transformed.text.spanStyles.any { it.start == 0 && it.end == 21 })
    }

    @Test
    fun muteTransformation_appliesConfiguredAlpha() {
        // “浅色半透明”是需求本身：断言传入的 alpha 被原样作用到时间戳行，
        // 且明显小于 1（与正文区分开）。
        val base = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
        val muted = base.copy(alpha = LeadingTimeStamp.MUTED_ALPHA)
        val text = "> 2026-11-02 04:53:29\n\n正文"

        val transformed = TimestampMuteTransformation(muted).filter(AnnotatedString(text))

        val stampSpan = transformed.text.spanStyles.firstOrNull { it.start == 0 }
        assertTrue("时间戳行应被染色", stampSpan != null)
        assertEquals(muted, stampSpan!!.item.color)
        // Compose Color 以 8 位通道量化，0.38 会存成 97/255≈0.38039，故用容差比较。
        assertEquals(
            LeadingTimeStamp.MUTED_ALPHA,
            stampSpan.item.color.alpha,
            1f / 255f,
        )
        assertTrue(
            "时间戳必须比正文浅（alpha 应明显小于 1）",
            stampSpan.item.color.alpha < 0.6f,
        )
    }

    @Test
    fun muteTransformation_doesNothingWithoutStamp() {
        val text = "普通正文"
        val transformed = TimestampMuteTransformation(androidx.compose.ui.graphics.Color.Red)
            .filter(AnnotatedString(text))
        assertEquals(text, transformed.text.text)
        assertTrue(transformed.text.spanStyles.isEmpty())
    }
}
