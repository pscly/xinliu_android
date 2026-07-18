package cc.pscly.onememos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight

/**
 * 阅读模式配置：字号档 × 行距档。
 *
 * - 由 [OneMemosTheme] 依据 [cc.pscly.onememos.domain.model.AppSettings] 提供；
 * - 列表正文 / 编辑器正文读取 [LocalReadingConfig] 缩放字号与行高；
 * - 未显式提供时默认 STANDARD × STANDARD，与 M1 schema 默认值一致。
 *
 * 字号映射（body 正文）：
 * | 档位        | fontSize | 基准 lineHeight |
 * |-------------|----------|-----------------|
 * | SMALL       | 13.sp    | 18.sp           |
 * | STANDARD    | 14.sp    | 20.sp           |
 * | LARGE       | 16.sp    | 24.sp           |
 * | EXTRA_LARGE | 18.sp    | 28.sp           |
 *
 * 行距档在基准 lineHeight 上乘系数：COMPACT 0.875 / STANDARD 1.0 / RELAXED 1.25。
 */
@Immutable
data class ReadingConfig(
    val fontScale: ReadingFontScale = ReadingFontScale.STANDARD,
    val lineHeight: ReadingLineHeight = ReadingLineHeight.STANDARD,
) {
    /** 阅读正文基准字号（sp）。 */
    val bodyFontSize: TextUnit
        get() =
            when (fontScale) {
                ReadingFontScale.SMALL -> 13.sp
                ReadingFontScale.STANDARD -> 14.sp
                ReadingFontScale.LARGE -> 16.sp
                ReadingFontScale.EXTRA_LARGE -> 18.sp
            }

    /**
     * 阅读正文字号对应的基准行高（未乘行距档系数）。
     */
    val baseBodyLineHeight: TextUnit
        get() =
            when (fontScale) {
                ReadingFontScale.SMALL -> 18.sp
                ReadingFontScale.STANDARD -> 20.sp
                ReadingFontScale.LARGE -> 24.sp
                ReadingFontScale.EXTRA_LARGE -> 28.sp
            }

    /** 应用行距档后的正文行高。 */
    val bodyLineHeight: TextUnit
        get() {
            val factor =
                when (lineHeight) {
                    ReadingLineHeight.COMPACT -> 0.875f
                    ReadingLineHeight.STANDARD -> 1f
                    ReadingLineHeight.RELAXED -> 1.25f
                }
            return (baseBodyLineHeight.value * factor).sp
        }

    /**
     * 将阅读字号/行高应用到既有 [TextStyle]（保留 fontFamily / color 等）。
     */
    fun applyTo(base: TextStyle): TextStyle =
        base.copy(
            fontSize = bodyFontSize,
            lineHeight = bodyLineHeight,
        )

    companion object {
        val Default: ReadingConfig = ReadingConfig()
    }
}

/**
 * 阅读模式（字号 / 行距）CompositionLocal。
 *
 * 组件层只读本 Local，不直接依赖 AppSettings 或 ThemeDescriptor。
 */
val LocalReadingConfig = staticCompositionLocalOf { ReadingConfig.Default }
