package cc.pscly.onememos.ui.util

import cc.pscly.onememos.domain.model.MemoLocation
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `withLeadingStamp`：新建笔记时在正文最开头插入 `> yyyy-MM-dd HH:mm:ss`。
 */
class QuickInsertTimeFormatterStampTest {
    private val now = 1_790_000_000_000L

    @Test
    fun withLeadingStamp_prependsQuotedLineAndKeepsBody() {
        val stamp = QuickInsertTimeFormatter.buildQuotedLine(now, QuickInsertTimeFormat.FULL_DATETIME)

        val result =
            QuickInsertTimeFormatter.withLeadingStamp(
                content = "正文第一行",
                epochMillis = now,
                format = QuickInsertTimeFormat.FULL_DATETIME,
            )

        assertTrue("应以引用行开头：$result", result.startsWith(stamp))
        assertTrue("正文必须保留：$result", result.endsWith("正文第一行"))
        // 时间戳与正文之间留一个空行，Markdown 渲染才是独立引用块
        assertEquals("$stamp\n\n正文第一行", result)
    }

    @Test
    fun withLeadingStamp_isIdempotentWhenAlreadyStamped() {
        val stamp = QuickInsertTimeFormatter.buildQuotedLine(now, QuickInsertTimeFormat.FULL_DATETIME)
        val already = "$stamp\n\n正文"

        val result =
            QuickInsertTimeFormatter.withLeadingStamp(
                content = already,
                epochMillis = now,
                format = QuickInsertTimeFormat.FULL_DATETIME,
            )

        assertEquals(already, result)
    }

    @Test
    fun withLeadingStamp_timeOnlyFormatUsesHms() {
        val stamp = QuickInsertTimeFormatter.buildQuotedLine(now, QuickInsertTimeFormat.TIME_ONLY)

        val result =
            QuickInsertTimeFormatter.withLeadingStamp(
                content = "x",
                epochMillis = now,
                format = QuickInsertTimeFormat.TIME_ONLY,
            )

        assertTrue(result.startsWith(stamp))
        assertFalse("仅时间格式不应带日期：$result", result.contains("-"))
    }

    @Test
    fun withLeadingStamp_emptyContentReturnsStampLineOnly() {
        val stamp = QuickInsertTimeFormatter.buildQuotedLine(now, QuickInsertTimeFormat.FULL_DATETIME)

        assertEquals(
            stamp,
            QuickInsertTimeFormatter.withLeadingStamp(
                content = "",
                epochMillis = now,
                format = QuickInsertTimeFormat.FULL_DATETIME,
            ),
        )
    }

    @Test
    fun memoLocation_ofRejectsMissingAndZeroCoordinates() {
        assertNull(MemoLocation.of(latitude = null, longitude = 1.0))
        assertNull(MemoLocation.of(latitude = 1.0, longitude = null))
        assertNull("0,0 是哨兵值，不应入库", MemoLocation.of(latitude = 0.0, longitude = 0.0))
        assertNull(MemoLocation.of(latitude = Double.NaN, longitude = 1.0))
        assertNull(MemoLocation.of(latitude = 91.0, longitude = 1.0))

        val valid = MemoLocation.of(latitude = 22.5431, longitude = 114.0579)
        assertTrue(valid != null)
        assertTrue(valid!!.isUsable)
    }
}
