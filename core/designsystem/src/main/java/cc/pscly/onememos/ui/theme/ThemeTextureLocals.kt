package cc.pscly.onememos.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import cc.pscly.onememos.domain.model.ThemeTexture

/**
 * 质感轴（文墨卷轴 / 清简）的 CompositionLocal。
 *
 * - 默认 [ThemeTexture.SCROLL]：保持既有“文墨卷轴”表现（横线 + 朱砂竖线等），
 *   未显式提供时所有组件行为与旧版一致；
 * - 由 `OneMemosTheme` 依据 `OneMemosThemeConfig.themeDescriptor.texture` 提供；
 * - 组件读取后按质感分支渲染，避免在组件层再感知 ThemeDescriptor 全貌。
 */
val LocalThemeTexture = staticCompositionLocalOf { ThemeTexture.SCROLL }

/**
 * 标签 chip 彩色开关的 CompositionLocal。
 *
 * - 默认 true：TagChip 按标签名 HSV 哈希生成柔和粉彩色；
 * - false：TagChip 统一使用退后灰（surfaceVariant 低透明度底）。
 * 由 `OneMemosTheme` 依据 `OneMemosThemeConfig.tagChipColorful` 提供。
 */
val LocalTagChipColorful = staticCompositionLocalOf { true }
