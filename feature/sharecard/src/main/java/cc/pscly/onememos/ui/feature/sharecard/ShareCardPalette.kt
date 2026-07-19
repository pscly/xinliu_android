package cc.pscly.onememos.ui.feature.sharecard

import androidx.compose.ui.graphics.Color

/**
 * 分享卡画布导出固定色（与屏幕主题无关，导出像素固定）。
 *
 * 这些颜色作用于离屏渲染的分享卡位图：导出图片在任何 App 主题下都必须一致，
 * 因此不取 MaterialTheme.colorScheme。按 M4-A3 规则本应上移到 core/designsystem
 * 的 InkTone，但 core 由并行代理负责，故先收敛在模块内本文件，后续可单点上移。
 */
internal object ShareCardPalette {
    val CanvasBlack = Color(0xFF000000) // 「光影」蒙版与纸纹线基色（配合 alpha 使用）
    val PaperSuLv = Color(0xFFFDFBF7) // 「素履」纸底 / 印章文字色
    val PaperMoRan = Color(0xFF1A1A1A) // 「墨染」底
    val PaperXuanZhi = Color(0xFFF7F2E9) // 「宣纸」底
    val PaperGuangYing = Color(0xFF101010) // 「光影」底
    val InkOnDark = Color(0xFFECECEC) // 深底正文 / 深底二维码衬纸
    val InkOnLight = Color(0xFF1E1E1E) // 浅底正文
    val AccentMoRan = Color(0xFFB6A37A) // 「墨染」强调金
    val Vermilion = Color(0xFFCA2A2A) // 朱砂红
    val PolaroidPaper = Color(0xFFFCFBF7) // 拍立得相纸底
    val QrPaperLight = Color(0xFFFFFFFF) // 浅底二维码衬纸
}
