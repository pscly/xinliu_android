package cc.pscly.onememos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cc.pscly.onememos.core.designsystem.R
import cc.pscly.onememos.domain.model.ThemeFontFamily

/** 霞鹜文楷全量 TTF（OFL，assets/licenses/OFL.txt）；仅标题档使用。 */
private val WenKaiFontFamily: FontFamily =
    FontFamily(Font(R.font.lxgw_wenkai, weight = FontWeight.Normal))

/**
 * 按 [ThemeFontFamily] 生成 Material3 Typography。
 * WENKAI：标题文楷 + 正文 SansSerif；SYSTEM：标题 Serif + 正文 SansSerif。
 * 字号/行高与既有尺度一致，仅切换 fontFamily。
 */
fun oneMemosTypography(fontFamily: ThemeFontFamily): Typography {
    val titleFamily =
        when (fontFamily) {
            ThemeFontFamily.WENKAI -> WenKaiFontFamily
            ThemeFontFamily.SYSTEM -> FontFamily.Serif
        }
    val bodyFamily = FontFamily.SansSerif

    return Typography(
        headlineLarge =
            TextStyle(
                fontFamily = titleFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = titleFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = bodyFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
    )
}

/** 默认文楷档，兼容预览与未注入描述符的调用点。 */
val OneMemosTypography: Typography = oneMemosTypography(ThemeFontFamily.WENKAI)
