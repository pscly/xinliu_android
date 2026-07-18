package cc.pscly.onememos.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import cc.pscly.onememos.domain.model.ThemeDensity

/**
 * 密度轴（标准 / 宽松 / 紧凑）的 CompositionLocal。
 *
 * - 默认 [ThemeDensity.STANDARD]：保持既有留白与间距；
 * - 由 `OneMemosTheme` 依据 `OneMemosThemeConfig.themeDescriptor.density` 提供；
 * - 组件读取后按密度调整 contentPadding、item 间距等布局参数，
 *   避免在组件层再感知 ThemeDescriptor 全貌。
 *
 * 密度→间距映射（见 HomeScreen 等消费点）：
 * | 密度    | contentPadding H | contentPadding V | item spacing |
 * |---------|------------------|------------------|-------------|
 * | COMPACT | InkSpacing.X8    | InkSpacing.X8    | InkSpacing.X8 |
 * | STANDARD| InkSpacing.X16   | InkSpacing.X12   | InkSpacing.X12 |
 * | RELAXED | InkSpacing.X24   | InkSpacing.X20   | InkSpacing.X20 |
 */
val LocalThemeDensity = staticCompositionLocalOf { ThemeDensity.STANDARD }
