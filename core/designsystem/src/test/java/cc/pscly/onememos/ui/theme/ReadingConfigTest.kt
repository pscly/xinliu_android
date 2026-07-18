package cc.pscly.onememos.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读模式字号/行距映射单测：锁住 M3.3 档位表，避免 UI 与令牌漂移。
 */
class ReadingConfigTest {
    @Test
    fun fontScale_mapsBodyFontSizeAndBaseLineHeight() {
        assertEquals(13f, ReadingConfig(fontScale = ReadingFontScale.SMALL).bodyFontSize.value)
        assertEquals(18f, ReadingConfig(fontScale = ReadingFontScale.SMALL).baseBodyLineHeight.value)

        assertEquals(14f, ReadingConfig(fontScale = ReadingFontScale.STANDARD).bodyFontSize.value)
        assertEquals(20f, ReadingConfig(fontScale = ReadingFontScale.STANDARD).baseBodyLineHeight.value)

        assertEquals(16f, ReadingConfig(fontScale = ReadingFontScale.LARGE).bodyFontSize.value)
        assertEquals(24f, ReadingConfig(fontScale = ReadingFontScale.LARGE).baseBodyLineHeight.value)

        assertEquals(18f, ReadingConfig(fontScale = ReadingFontScale.EXTRA_LARGE).bodyFontSize.value)
        assertEquals(28f, ReadingConfig(fontScale = ReadingFontScale.EXTRA_LARGE).baseBodyLineHeight.value)
    }

    @Test
    fun lineHeight_appliesCompactStandardRelaxedFactors() {
        val base = ReadingConfig(fontScale = ReadingFontScale.STANDARD)
        assertEquals(20f, base.bodyLineHeight.value)

        val compact =
            ReadingConfig(
                fontScale = ReadingFontScale.STANDARD,
                lineHeight = ReadingLineHeight.COMPACT,
            )
        assertEquals(17.5f, compact.bodyLineHeight.value)

        val relaxed =
            ReadingConfig(
                fontScale = ReadingFontScale.STANDARD,
                lineHeight = ReadingLineHeight.RELAXED,
            )
        assertEquals(25f, relaxed.bodyLineHeight.value)
    }

    @Test
    fun applyTo_overridesFontSizeAndLineHeight_keepsOtherFields() {
        val base = TextStyle(fontSize = 99.sp, lineHeight = 99.sp)
        val applied =
            ReadingConfig(
                fontScale = ReadingFontScale.LARGE,
                lineHeight = ReadingLineHeight.STANDARD,
            ).applyTo(base)
        assertEquals(16f, applied.fontSize.value)
        assertEquals(24f, applied.lineHeight.value)
    }
}
