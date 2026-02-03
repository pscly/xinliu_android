package cc.pscly.onememos.ui.feature.sharecard

import android.graphics.Bitmap

enum class ShareCardTheme(
    val displayName: String,
) {
    // plan4：素履（默认）
    SU_LV("素履"),
    // plan4：墨染（深色）
    MO_RAN("墨染"),
    // plan4：宣纸（拟物）
    XUAN_ZHI("宣纸"),
    // plan4：光影（有图时更适合）
    GUANG_YING("光影"),
}

enum class ShareCardRatio(
    val displayName: String,
) {
    AUTO("自适应"),
    SQUARE_1_1("1:1"),
    STORY_9_16("9:16"),
}

enum class ShareCardFontSize(
    val displayName: String,
) {
    SMALL("小"),
    MEDIUM("中"),
    LARGE("大"),
}

enum class ShareCardAlign(
    val displayName: String,
) {
    LEFT("左对齐"),
    CENTER("居中"),
}

enum class ShareCardLongExportMode(
    val displayName: String,
) {
    PAGED("分页（推荐）"),
    SINGLE("单张（可能耗内存）"),
}

data class ShareCardUiState(
    val uuid: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val exportError: String? = null,
    val exportProgressText: String? = null,
    val content: String = "",
    val createdAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val backgroundBitmap: Bitmap? = null,
    // 轻量预览图：用于“拍立得/生活感”排版；最多取几张即可，避免内存抖动。
    val photoBitmaps: List<Bitmap> = emptyList(),
    // 署名：用于底部作者与动态印章（可在“更多”里改）。
    val authorName: String = "",
    val theme: ShareCardTheme = ShareCardTheme.SU_LV,
    val ratio: ShareCardRatio = ShareCardRatio.AUTO,
    val fontSize: ShareCardFontSize = ShareCardFontSize.MEDIUM,
    val align: ShareCardAlign = ShareCardAlign.LEFT,
    // 长文模式：开启后导出时按内容高度生成“长图/分页长图”，默认关闭避免 OOM。
    val longMode: Boolean = false,
    val longExportMode: ShareCardLongExportMode = ShareCardLongExportMode.PAGED,
    // 二维码：可选显示/隐藏；默认文案可改（可在“更多”里改）。
    val qrEnabled: Boolean = false,
    val qrText: String = "",
    val qrBitmap: Bitmap? = null,
    val saving: Boolean = false,
    val lastSavedPath: String? = null,
)
