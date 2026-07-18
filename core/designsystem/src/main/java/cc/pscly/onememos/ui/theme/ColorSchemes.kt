package cc.pscly.onememos.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cc.pscly.onememos.domain.model.ThemePalette

// 字面量仅集中于此文件（M1.6 色板数据化，M2.10 全令牌化消除 M3 默认残留）

private val PaperBg = Color(0xFFF7F8F4) // 草白
private val PaperSurface = Color(0xFFFDFBF3) // 米宣纸
private val InkText = Color(0xFF383431) // 焦茶
private val InkSubText = Color(0xFF878885) // 雅灰

private val Vermilion = Color(0xFFFF4C39) // 朱砂
private val Indigo = Color(0xFF305169) // 黛蓝

private val NightBg = Color(0xFF18191B) // 墨灰
private val NightSurface = Color(0xFF22252A) // 玄青
private val NightText = Color(0xFFE0E0E0) // 银灰

private val Gold = Color(0xFFF2BE45) // 赤金
private val NeonCyan = Color(0xFF00E5BC) // 荧光青

/** 月白暗色主色：柔和米灰 */
private val MoonPrimaryDark = Color(0xFFD9D4CD)
private val MoonOnPrimaryDark = Color(0xFF2A2826)

/** 月白暗色次色：中性灰 */
private val MoonSecondaryDark = Color(0xFFA3A09B)
private val MoonOnSecondaryDark = Color(0xFF1C1B19)

// ---------- 浅色纸系容器阶（替代 M3 默认紫灰容器） ----------
private val PaperSurfaceDim = Color(0xFFD7D4C9)
private val PaperContainerLowest = Color(0xFFFFFCF6)
private val PaperContainerLow = Color(0xFFFAF7EE)
private val PaperContainer = Color(0xFFF2F0E6)
private val PaperContainerHigh = Color(0xFFECE9DE)
private val PaperContainerHighest = Color(0xFFE5E2D7)
private val PaperSurfaceVariant = Color(0xFFEDEBE1)
private val PaperOutlineVariant = Color(0xFFC9C7BB)

// ---------- 深色墨系容器阶 ----------
private val NightSurfaceDim = Color(0xFF121417)
private val NightSurfaceBright = Color(0xFF34383F)
private val NightContainerLowest = Color(0xFF0F1113)
private val NightContainerLow = Color(0xFF1B1E23)
private val NightContainer = Color(0xFF1F2228)
private val NightContainerHigh = Color(0xFF25292F)
private val NightContainerHighest = Color(0xFF30343B)
private val NightSurfaceVariant = Color(0xFF2E3238)
private val NightOnSurfaceVariant = Color(0xFFB9BCB3)
private val NightOutlineVariant = Color(0xFF3F444C)
private val NightInverseOnSurface = Color(0xFF22262B)

/**
 * 浅色 Material3 [ColorScheme]。
 *
 * [context] 仅 [ThemePalette.DYNAMIC] 需要：API 31+ 取系统动态色；
 * 无 Context 或 API 低于 31 时回退 [ThemePalette.PAPER_INK]。
 */
fun oneMemosLightColorScheme(
    palette: ThemePalette,
    context: Context? = null,
): ColorScheme =
    when (palette) {
        ThemePalette.PAPER_INK ->
            paperInkLightScheme(
                primary = Vermilion,
                onPrimary = Color.White,
                secondary = Indigo,
                onSecondary = Color.White,
                inversePrimary = Gold,
            )

        ThemePalette.INDIGO ->
            paperInkLightScheme(
                primary = Indigo,
                onPrimary = Color.White,
                secondary = Vermilion,
                onSecondary = Color.White,
                inversePrimary = Gold,
            )

        ThemePalette.CYBER ->
            paperInkLightScheme(
                primary = NeonCyan,
                onPrimary = Color(0xFF0B1F1A),
                secondary = Vermilion,
                onSecondary = Color.White,
                inversePrimary = NeonCyan,
            )

        ThemePalette.MOON_WHITE ->
            paperInkLightScheme(
                primary = InkText,
                onPrimary = Color.White,
                secondary = InkSubText,
                onSecondary = Color.White,
                inversePrimary = MoonPrimaryDark,
            )

        ThemePalette.DYNAMIC ->
            resolveDynamicLight(context)
    }

/**
 * 深色 Material3 [ColorScheme]。
 *
 * [context] 语义同 [oneMemosLightColorScheme]。
 */
fun oneMemosDarkColorScheme(
    palette: ThemePalette,
    context: Context? = null,
): ColorScheme =
    when (palette) {
        ThemePalette.PAPER_INK ->
            paperInkDarkScheme(
                primary = Gold,
                onPrimary = Color(0xFF1E1300),
                secondary = Vermilion,
                onSecondary = Color.White,
                inversePrimary = Vermilion,
            )

        ThemePalette.INDIGO ->
            paperInkDarkScheme(
                primary = Indigo,
                onPrimary = Color.White,
                secondary = Gold,
                onSecondary = Color(0xFF1E1300),
                inversePrimary = Indigo,
            )

        ThemePalette.CYBER ->
            paperInkDarkScheme(
                primary = NeonCyan,
                onPrimary = Color(0xFF0B1F1A),
                secondary = Gold,
                onSecondary = Color(0xFF1E1300),
                inversePrimary = NeonCyan,
            )

        ThemePalette.MOON_WHITE ->
            paperInkDarkScheme(
                primary = MoonPrimaryDark,
                onPrimary = MoonOnPrimaryDark,
                secondary = MoonSecondaryDark,
                onSecondary = MoonOnSecondaryDark,
                inversePrimary = InkText,
            )

        ThemePalette.DYNAMIC ->
            resolveDynamicDark(context)
    }

/**
 * 浅色纸系全令牌骨架：只注入各色板的主/次/反色，其余表面阶固定取自纸墨色系。
 * 任何未显式指定的槽位都会落回 M3 默认蓝紫，故此处必须全量覆盖。
 */
private fun paperInkLightScheme(
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    onSecondary: Color,
    inversePrimary: Color,
): ColorScheme =
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        background = PaperBg,
        onBackground = InkText,
        surface = PaperSurface,
        onSurface = InkText,
        surfaceVariant = PaperSurfaceVariant,
        onSurfaceVariant = InkSubText,
        surfaceTint = primary,
        inverseSurface = InkText,
        inverseOnSurface = PaperSurface,
        inversePrimary = inversePrimary,
        surfaceDim = PaperSurfaceDim,
        surfaceBright = PaperSurface,
        surfaceContainerLowest = PaperContainerLowest,
        surfaceContainerLow = PaperContainerLow,
        surfaceContainer = PaperContainer,
        surfaceContainerHigh = PaperContainerHigh,
        surfaceContainerHighest = PaperContainerHighest,
        outline = InkSubText,
        outlineVariant = PaperOutlineVariant,
        scrim = Color.Black,
    )

/** 深色墨系全令牌骨架：语义同 [paperInkLightScheme]。 */
private fun paperInkDarkScheme(
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    onSecondary: Color,
    inversePrimary: Color,
): ColorScheme =
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        background = NightBg,
        onBackground = NightText,
        surface = NightSurface,
        onSurface = NightText,
        surfaceVariant = NightSurfaceVariant,
        onSurfaceVariant = NightOnSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = NightText,
        inverseOnSurface = NightInverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceDim = NightSurfaceDim,
        surfaceBright = NightSurfaceBright,
        surfaceContainerLowest = NightContainerLowest,
        surfaceContainerLow = NightContainerLow,
        surfaceContainer = NightContainer,
        surfaceContainerHigh = NightContainerHigh,
        surfaceContainerHighest = NightContainerHighest,
        outline = Color(0xFF6D6E6E),
        outlineVariant = NightOutlineVariant,
        scrim = Color.Black,
    )

private fun resolveDynamicLight(context: Context?): ColorScheme =
    if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        oneMemosLightColorScheme(ThemePalette.PAPER_INK)
    }

private fun resolveDynamicDark(context: Context?): ColorScheme =
    if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        oneMemosDarkColorScheme(ThemePalette.PAPER_INK)
    }
