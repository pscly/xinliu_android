package cc.pscly.onememos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.isSystemInDarkTheme
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette

@Immutable
data class OneMemosThemeConfig(
    val palette: ThemePalette = ThemePalette.PAPER_INK,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
)

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

    val colorScheme = if (effectiveDark) {
        oneMemosDarkColorScheme(config.palette)
    } else {
        oneMemosLightColorScheme(config.palette)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OneMemosTypography,
        content = content,
    )
}
