package cc.pscly.onememos.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {
    @Test
    fun format_zero() {
        assertEquals("0 B", ByteSizeFormatter.format(0))
    }

    @Test
    fun format_bytes() {
        assertEquals("512 B", ByteSizeFormatter.format(512))
    }

    @Test
    fun format_kb() {
        assertEquals("1.0 KB", ByteSizeFormatter.format(1024))
    }

    @Test
    fun format_mb_rounding() {
        // 10MB 以上不显示小数，避免 UI 太花。
        assertEquals("12 MB", ByteSizeFormatter.format(12L * 1024 * 1024))
    }
}

