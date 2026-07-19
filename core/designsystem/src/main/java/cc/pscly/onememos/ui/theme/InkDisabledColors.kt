package cc.pscly.onememos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 禁用视觉令牌（DESIGN.md §8.2「禁用视觉不完整」核销）。
 *
 * M3 惯例：
 * - 禁用容器 = [ColorScheme.onSurface] × [ContainerAlpha]（0.12）
 * - 禁用内容 = [ColorScheme.onSurface] × [ContentAlpha]（0.38）
 *
 * 由 [OneMemosTheme] 依据当前 [ColorScheme] 推导后经 [LocalInkDisabledColors] 下发，
 * 随主题档/明暗自动变化；禁用态对比度享 WCAG inactive 豁免，语义 `disabled()` 必须保留。
 *
 * 不写入 [InkTone]：InkTone 仅收固定色，禁用色依赖主题档 onSurface。
 */
@Immutable
data class InkDisabledColors(
    val container: Color,
    val content: Color,
)

/** M3 禁用容器 alpha（onSurface 之上）。 */
const val InkDisabledContainerAlpha = 0.12f

/** M3 禁用内容 alpha（onSurface 之上）。 */
const val InkDisabledContentAlpha = 0.38f

/**
 * 从 [ColorScheme] 推导禁用色对。
 *
 * 仅做 onSurface alpha 推导，不重排既有角色映射。
 */
fun inkDisabledColorsOf(scheme: ColorScheme): InkDisabledColors =
    InkDisabledColors(
        container = scheme.onSurface.copy(alpha = InkDisabledContainerAlpha),
        content = scheme.onSurface.copy(alpha = InkDisabledContentAlpha),
    )

/**
 * 禁用色 CompositionLocal。
 *
 * 默认回退 lightColorScheme 推导，与 MaterialTheme 默认 light 对齐；
 * 正式路径由 [OneMemosTheme] 用当前 colorScheme 覆盖。
 */
val LocalInkDisabledColors =
    staticCompositionLocalOf {
        inkDisabledColorsOf(lightColorScheme())
    }
