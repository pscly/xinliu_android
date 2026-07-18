package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette

/**
 * 主题配置：描述符 + 明暗模式 + 阅读字号/行距。
 *
 * [palette] 保留为便捷属性（从 descriptor 推导），兼容既有调用点
 * `OneMemosThemeConfig(palette = ..., themeMode = ...)`。
 */
@Immutable
data class OneMemosThemeConfig(
    val themeDescriptor: ThemeDescriptor = ThemeDescriptor.WENMO_ZHUSHA,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val readingFontScale: ReadingFontScale = ReadingFontScale.STANDARD,
    val readingLineHeight: ReadingLineHeight = ReadingLineHeight.STANDARD,
) {
    /** 兼容旧构造：只传色板时映射为完整描述符。 */
    constructor(
        palette: ThemePalette,
        themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
        readingFontScale: ReadingFontScale = ReadingFontScale.STANDARD,
        readingLineHeight: ReadingLineHeight = ReadingLineHeight.STANDARD,
    ) : this(
        themeDescriptor = ThemeDescriptor.fromLegacyPalette(palette),
        themeMode = themeMode,
        readingFontScale = readingFontScale,
        readingLineHeight = readingLineHeight,
    )

    /** 色板轴便捷访问（与 [themeDescriptor.palette] 相同）。 */
    val palette: ThemePalette
        get() = themeDescriptor.palette

    /** 阅读模式便捷视图（字号 × 行距）。 */
    val readingConfig: ReadingConfig
        get() =
            ReadingConfig(
                fontScale = readingFontScale,
                lineHeight = readingLineHeight,
            )
}

@Composable
fun OneMemosTheme(
    config: OneMemosThemeConfig = OneMemosThemeConfig(),
    content: @Composable () -> Unit,
) {
    // 预览模式下尽量减少动态依赖，避免 Preview 偶发崩溃
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

    // 质感轴下发：组件读取 LocalThemeTexture 按文墨卷轴/清简分支渲染
    // 密度轴下发：组件读取 LocalThemeDensity 按标准/宽松/紧凑调整留白与间距
    // 阅读轴下发：列表/编辑器正文读取 LocalReadingConfig 缩放字号与行高
    CompositionLocalProvider(
        LocalThemeTexture provides config.themeDescriptor.texture,
        LocalThemeDensity provides config.themeDescriptor.density,
        LocalReadingConfig provides config.readingConfig,
    ) {
        // M2.10：shapes 轴同步纸墨圆角，AlertDialog/BottomSheet 等 M3 默认值随令牌落地
        MaterialTheme(
            colorScheme = colorScheme,
            typography = oneMemosTypography(config.themeDescriptor.fontFamily),
            shapes = PaperInkShapes,
            content = content,
        )
    }
}
