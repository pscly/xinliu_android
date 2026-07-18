package cc.pscly.onememos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import cc.pscly.onememos.domain.model.ThemePalette
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

    /**
     * M1.9 / M1.6：策展色板 × 明/暗 WCAG AA 对比度断言。
     *
     * 覆盖 PAPER_INK / INDIGO / CYBER / MOON_WHITE（4 套策展）。
     * [ThemePalette.DYNAMIC] 不进本矩阵（随系统尽力而为、不自动断言）。
     *
     * 断言对：
     * - 普通文本 ≥4.5:1 — onBackground/background、onSurface/surface
     * - 大文本/控件 ≥3:1 — onPrimary/primary、onSecondary/secondary
     */
@RunWith(Parameterized::class)
class WcagContrastTest(
    private val label: String,
    private val scheme: ColorScheme,
) {
    @Test
    fun normalText_onBackground_meets_4_5() {
        assertContrast(
            pair = "onBackground/background",
            foreground = scheme.onBackground,
            background = scheme.background,
            minRatio = WcagContrast.NORMAL_TEXT_MIN,
        )
    }

    @Test
    fun normalText_onSurface_meets_4_5() {
        assertContrast(
            pair = "onSurface/surface",
            foreground = scheme.onSurface,
            background = scheme.surface,
            minRatio = WcagContrast.NORMAL_TEXT_MIN,
        )
    }

    @Test
    fun largeTextOrControl_onPrimary_meets_3_0() {
        assertContrast(
            pair = "onPrimary/primary",
            foreground = scheme.onPrimary,
            background = scheme.primary,
            minRatio = WcagContrast.LARGE_TEXT_OR_UI_MIN,
        )
    }

    @Test
    fun largeTextOrControl_onSecondary_meets_3_0() {
        assertContrast(
            pair = "onSecondary/secondary",
            foreground = scheme.onSecondary,
            background = scheme.secondary,
            minRatio = WcagContrast.LARGE_TEXT_OR_UI_MIN,
        )
    }

    private fun assertContrast(
        pair: String,
        foreground: Color,
        background: Color,
        minRatio: Double,
    ) {
        val ratio = WcagContrast.contrastRatio(foreground, background)
        assertTrue(
            "$label $pair contrast=$ratio expected>=$minRatio " +
                "(fg=${foreground.toHex()} bg=${background.toHex()})",
            ratio >= minRatio,
        )
    }

    private fun Color.toHex(): String {
        val argb = (0xFFu shl 24) or
            ((red * 255f + 0.5f).toUInt() and 0xFFu shl 16) or
            ((green * 255f + 0.5f).toUInt() and 0xFFu shl 8) or
            ((blue * 255f + 0.5f).toUInt() and 0xFFu)
        return "#%08X".format(argb.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        /**
         * 策展色板注册表：枚举 entries 排除 [ThemePalette.DYNAMIC]。
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun curatedSchemes(): Collection<Array<Any>> {
            val cases = mutableListOf<Array<Any>>()
            for (palette in ThemePalette.entries.filter { it != ThemePalette.DYNAMIC }) {
                cases += arrayOf(
                    "${palette.name}/light",
                    oneMemosLightColorScheme(palette),
                )
                cases += arrayOf(
                    "${palette.name}/dark",
                    oneMemosDarkColorScheme(palette),
                )
            }
            return cases
        }
    }
}
