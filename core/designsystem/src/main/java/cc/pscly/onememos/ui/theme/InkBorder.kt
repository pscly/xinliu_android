package cc.pscly.onememos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 描边宽度与透明度令牌（数值来源：DESIGN.md §5 各原语外观、§7 深度策略）。
 *
 * 现有系统以“纸面色阶 + 细描边 + 自绘线条”表达层次，不使用投影；
 * 因此描边宽度与各色在 outline/primary 之上的透明度是核心令牌。
 */
object InkBorder {
    // ---------- 描边宽度 ----------
    val Hairline = 1.dp // 卡片/纸面/Chip/标签外框与自绘线条
    val Stamp = 2.dp // 盖章印记描边
    val TableCell = 0.5.dp // 表格单元格细分隔线
    val CanvasStroke = 3.dp // 分享卡画布竖线描边（M6）
    val CalendarRing = 1.6.dp // 个人中心日历今日描边（M6）
    val SpinnerStroke = Stamp // 按钮加载态 CPI 描边（复用 Stamp 值，不重复字面量）

    // ---------- 描边透明度（作用于 colorScheme.outline） ----------
    const val OutlineStrong = 0.45f // 卡片与纸面外框
    const val OutlineSoft = 0.22f // 纸面横线、分隔线
    const val OutlineSelected = 0.80f // 选中态描边
    const val OutlineIdle = 0.40f // InkChip 未选中描边
    const val TagIdle = 0.35f // TagChip 未选中描边
    const val TableOutline = 0.35f // 表格描边

    // ---------- 线条 / 块面透明度（作用于 colorScheme.primary 等） ----------
    const val MarginLine = 0.35f // 左侧朱砂竖线
    const val QuoteBar = 0.30f // 引用竖条
    const val ChipFillSelected = 0.14f // InkChip 选中底色
    const val TableHeaderFill = 0.55f // 表头底色（surfaceVariant 之上）
    const val StampFill = 0.10f // 印记底色
    const val StampOutline = 0.85f // 印记描边
    const val StampText = 0.90f // 印记文字
    const val StampScrim = 0.10f // 盖章遮罩（黑色之上再乘动画透明度）

    // ---------- 文字强调透明度 ----------
    const val PreviewText = 0.92f // 解析中快速预览正文
    const val QuoteText = 0.88f // 引用正文
}

/**
 * 固定色（不随主题色板变化的设计事实，见 DESIGN.md §2.1）。
 *
 * 仅收容各原语中真实存在、且不属于动态 colorScheme 的少量颜色；
 * M1.6 色板任务若要归并，可从本对象单点收口。
 */
object InkTone {
    val TagTextOnLight = Color(0xFF1C1C1C) // 浅底标签上的深文字
    val TagTextOnDark = Color(0xFFF3F3F3) // 深底标签上的浅文字
    val PaperEdge = Color(0xFFF3E7C9) // 图片查看器宣纸毛边暖色
    val VermilionLine = Color(0xFFB44A3A) // 图片查看器朱砂细线（偏棕红）
    val ViewerBackdrop = Color(0xFF050403) // 图片查看器近黑底
    val InlineCodeBg = Color(0x22000000) // 行内代码底色
}
