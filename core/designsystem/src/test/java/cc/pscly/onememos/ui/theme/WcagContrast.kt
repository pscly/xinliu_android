package cc.pscly.onememos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG 2.x 对比度工具（纯 JVM，基于 Compose [Color.luminance]）。
 *
 * 相对亮度 L 已由 Compose 按 sRGB 线性化计算；对比度公式：
 * (L_light + 0.05) / (L_dark + 0.05)
 *
 * 阈值：普通文本 AA ≥ 4.5:1；大文本/非文本控件 AA ≥ 3:1。
 */
object WcagContrast {
    const val NORMAL_TEXT_MIN = 4.5
    const val LARGE_TEXT_OR_UI_MIN = 3.0

    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val light = max(l1, l2)
        val dark = min(l1, l2)
        return (light + 0.05) / (dark + 0.05)
    }

    fun meetsNormalText(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= NORMAL_TEXT_MIN

    fun meetsLargeTextOrUi(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= LARGE_TEXT_OR_UI_MIN
}
