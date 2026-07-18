package cc.pscly.onememos.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 对比度公式与阈值的独立回归（不依赖主题色板）。
 */
class WcagContrastMathTest {
    @Test
    fun blackOnWhite_is21() {
        val ratio = WcagContrast.contrastRatio(Color.Black, Color.White)
        assertEquals(21.0, ratio, 0.01)
    }

    @Test
    fun sameColor_is1() {
        val ratio = WcagContrast.contrastRatio(Color.Gray, Color.Gray)
        assertEquals(1.0, ratio, 0.01)
    }

    @Test
    fun orderIndependent() {
        val a = WcagContrast.contrastRatio(Color.Black, Color.White)
        val b = WcagContrast.contrastRatio(Color.White, Color.Black)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun paperInkKnownPair_meetsNormalText() {
        // 与 ColorSchemes 中 PaperBg/InkText 一致的字面量，防止主题漂移时公式误报
        val paperBg = Color(0xFFF7F8F4)
        val inkText = Color(0xFF383431)
        assertTrue(WcagContrast.meetsNormalText(inkText, paperBg))
    }
}
