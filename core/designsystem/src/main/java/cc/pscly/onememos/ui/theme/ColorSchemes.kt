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

// 字面量仅集中于此文件（M1.6 色板数据化）

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
            lightColorScheme(
                primary = Vermilion,
                onPrimary = Color.White,
                secondary = Indigo,
                onSecondary = Color.White,
                background = PaperBg,
                onBackground = InkText,
                surface = PaperSurface,
                onSurface = InkText,
                outline = InkSubText,
            )

        ThemePalette.INDIGO ->
            lightColorScheme(
                primary = Indigo,
                onPrimary = Color.White,
                secondary = Vermilion,
                onSecondary = Color.White,
                background = PaperBg,
                onBackground = InkText,
                surface = PaperSurface,
                onSurface = InkText,
                outline = InkSubText,
            )

        ThemePalette.CYBER ->
            lightColorScheme(
                primary = NeonCyan,
                onPrimary = Color(0xFF0B1F1A),
                secondary = Vermilion,
                onSecondary = Color.White,
                background = PaperBg,
                onBackground = InkText,
                surface = PaperSurface,
                onSurface = InkText,
                outline = InkSubText,
            )

        ThemePalette.MOON_WHITE ->
            lightColorScheme(
                primary = InkText,
                onPrimary = Color.White,
                secondary = InkSubText,
                onSecondary = Color.White,
                background = PaperBg,
                onBackground = InkText,
                surface = PaperSurface,
                onSurface = InkText,
                outline = InkSubText,
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
            darkColorScheme(
                primary = Gold,
                onPrimary = Color(0xFF1E1300),
                secondary = Vermilion,
                onSecondary = Color.White,
                background = NightBg,
                onBackground = NightText,
                surface = NightSurface,
                onSurface = NightText,
                outline = Color(0xFF6D6E6E),
            )

        ThemePalette.INDIGO ->
            darkColorScheme(
                primary = Indigo,
                onPrimary = Color.White,
                secondary = Gold,
                onSecondary = Color(0xFF1E1300),
                background = NightBg,
                onBackground = NightText,
                surface = NightSurface,
                onSurface = NightText,
                outline = Color(0xFF6D6E6E),
            )

        ThemePalette.CYBER ->
            darkColorScheme(
                primary = NeonCyan,
                onPrimary = Color(0xFF0B1F1A),
                secondary = Gold,
                onSecondary = Color(0xFF1E1300),
                background = NightBg,
                onBackground = NightText,
                surface = NightSurface,
                onSurface = NightText,
                outline = Color(0xFF6D6E6E),
            )

        ThemePalette.MOON_WHITE ->
            darkColorScheme(
                primary = MoonPrimaryDark,
                onPrimary = MoonOnPrimaryDark,
                secondary = MoonSecondaryDark,
                onSecondary = MoonOnSecondaryDark,
                background = NightBg,
                onBackground = NightText,
                surface = NightSurface,
                onSurface = NightText,
                outline = Color(0xFF6D6E6E),
            )

        ThemePalette.DYNAMIC ->
            resolveDynamicDark(context)
    }

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
