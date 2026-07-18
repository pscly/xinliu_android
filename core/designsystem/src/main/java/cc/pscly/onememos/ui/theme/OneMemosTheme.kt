package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette

/**
 * 主题配置：描述符 + 明暗模式。
 *
 * [palette] 保留为便捷属性（从 descriptor 推导），兼容既有调用点
 * `OneMemosThemeConfig(palette = ..., themeMode = ...)`。
 */
@Immutable
data class OneMemosThemeConfig(
    val themeDescriptor: ThemeDescriptor = ThemeDescriptor.WENMO_ZHUSHA,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
) {
    /** 兼容旧构造：只传色板时映射为完整描述符。 */
    constructor(
        palette: ThemePalette,
        themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    ) : this(
        themeDescriptor = ThemeDescriptor.fromLegacyPalette(palette),
        themeMode = themeMode,
    )

    /** 色板轴便捷访问（与 [themeDescriptor.palette] 相同）。 */
    val palette: ThemePalette
        get() = themeDescriptor.palette
}

@Composable
fun OneMemosTheme(
    config: OneMemosThemeConfig = OneMemosThemeConfig(),
    content: @Composable () -> Unit,
) {
    // 预览模式下尽量减少“动态依赖”，避免 Preview 偶发崩溃
    val isPreview = LocalInspectionMode.current
    val systemDark = if (isPreview) false else isSystemInDarkTheme()
    val effectiveDark = when (config.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // DYNAMIC 色板需要 Context（API 31+ Material You）；预览无系统壁纸时回退 PAPER_INK
    val context = if (isPreview) null else LocalContext.current
    val colorScheme = if (effectiveDark) {
        oneMemosDarkColorScheme(config.palette, context)
    } else {
        oneMemosLightColorScheme(config.palette, context)
    }

    // 质感轴下发：组件读取 LocalThemeTexture 按“文墨卷轴 / 清简”分支渲染
    CompositionLocalProvider(LocalThemeTexture provides config.themeDescriptor.texture) {
        MaterialTheme(
            colorScheme = colorScheme,
                typography = oneMemosTypography(config.themeDescriptor.fontFamily),
            content = content,
        )
    }
}
