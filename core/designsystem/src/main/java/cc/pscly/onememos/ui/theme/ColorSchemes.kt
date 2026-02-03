package cc.pscly.onememos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cc.pscly.onememos.domain.model.ThemePalette

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

fun oneMemosLightColorScheme(palette: ThemePalette) = when (palette) {
    ThemePalette.PAPER_INK -> lightColorScheme(
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

    ThemePalette.INDIGO -> lightColorScheme(
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

    ThemePalette.CYBER -> lightColorScheme(
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
}

fun oneMemosDarkColorScheme(palette: ThemePalette) = when (palette) {
    ThemePalette.PAPER_INK -> darkColorScheme(
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

    ThemePalette.INDIGO -> darkColorScheme(
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

    ThemePalette.CYBER -> darkColorScheme(
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
}
