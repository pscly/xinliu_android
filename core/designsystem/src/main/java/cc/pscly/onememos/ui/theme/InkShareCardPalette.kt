package cc.pscly.onememos.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 分享卡离屏导出的固定调色板。
 *
 * 这些颜色定义最终图片的像素，不随 App 明暗模式或主题色板变化，因此集中在设计系统，
 * 禁止业务屏重复声明色值。
 */
object InkShareCardPalette {
    val CanvasBlack = Color(0xFF000000)
    val PaperSuLv = Color(0xFFFDFBF7)
    val PaperMoRan = Color(0xFF1A1A1A)
    val PaperXuanZhi = Color(0xFFF7F2E9)
    val PaperGuangYing = Color(0xFF101010)
    val InkOnDark = Color(0xFFECECEC)
    val InkOnLight = Color(0xFF1E1E1E)
    val AccentMoRan = Color(0xFFB6A37A)
    val Vermilion = Color(0xFFCA2A2A)
    val PolaroidPaper = Color(0xFFFCFBF7)
    val QrPaperLight = Color(0xFFFFFFFF)
}
