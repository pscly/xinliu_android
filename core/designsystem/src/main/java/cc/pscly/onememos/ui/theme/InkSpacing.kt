package cc.pscly.onememos.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 间距与布局令牌（数值来源：DESIGN.md §4.1 已观察尺寸表）。
 *
 * 约定：
 * - `X*` 为数值尺度，尽量只在本文件内出现一次；
 * - 语义别名引用尺度值，组件只使用语义别名或尺度名，不再写裸 dp/sp；
 * - 纸面横线节距（30sp）同时是编辑与 Markdown 长文的正文行高。
 */
object InkSpacing {
    // ---------- 数值尺度（DESIGN.md §4.1） ----------
    val X1 = 1.dp // 发丝线：外框、纸面横线、朱砂竖线线宽
    val X2 = 2.dp // TagChip 垂直内边距（紧凑）
    val X4 = 4.dp // 紧凑间距（列表/图标间隙等值映射）
    val X6 = 6.dp // 底部弹层标题间距 / 朱砂细线框内缩
    val X8 = 8.dp // InkChip / 表格单元格垂直内边距
    val X10 = 10.dp // TagChip 水平内边距、引用间隔
    val X12 = 12.dp // 纸面上下内边距、Markdown 块间距与代码块内边距
    val X14 = 14.dp // InkCard 内边距
    val X16 = 16.dp // 纸面右侧内边距
    val X20 = 20.dp // TagFilterBottomSheet 水平页边距
    val X24 = 24.dp // 朱砂竖线距左位置 / 引用条最小高度
    val X34 = 34.dp // 纸面内容左内边距（为竖线保留文字起始空间）
    val X44 = 44.dp // SealIconButton 默认尺寸 / 印章紧凑阈值
    val X56 = 56.dp // SealButton 默认尺寸
    val X88 = 88.dp // Markdown 表格单元格最小宽度
    val X150 = 150.dp // 盖章印记固定尺寸

    // ---------- 行高（sp） ----------
    val LinePitch = 30.sp // 纸面横线节距 = 正文行高
    val CodeLineHeight = 20.sp // 代码块行高

    // ---------- 语义别名：纸面 ----------
    val PaperPaddingStart = X34
    val PaperPaddingEnd = X16
    val PaperPaddingV = X12
    val MarginLineX = X24

    // ---------- 语义别名：纸面（清简 MINIMAL 质感，更大留白） ----------
    val PaperPaddingStartMinimal = X20
    val PaperPaddingEndMinimal = X20
    val PaperPaddingVMinimal = X16

    // ---------- 语义别名：卡片 / Chip / 标签 ----------
    val CardPadding = X14
    val ChipPaddingH = X12
    val ChipPaddingV = X8
    val TagPaddingH = X10
    val TagPaddingV = X2

    // ---------- 语义别名：印章控件 ----------
    val SealButtonSize = X56
    val SealIconSize = X44
    val SealCompactThreshold = X44 // 尺寸不大于该值时使用紧凑圆角与 titleMedium
    val StampSize = X150

    // ---------- 语义别名：Markdown ----------
    val MarkdownBlockGap = X12 // 完整阅读块间距
    val MarkdownPreviewGap = X10 // 列表预览块间距
    val CodeBlockPadding = X12
    val QuoteBarWidth = 3.dp // 引用竖条宽度（未列入 §4.1，原语真实使用）
    val QuoteBarMinHeight = X24
    val QuoteGap = X10 // 引用竖条与正文间隔
    val TableCellMinWidth = X88
    val TableCellPaddingH = X10
    val TableCellPaddingV = X8

    // ---------- 语义别名：底部弹层 ----------
    val SheetMarginH = X20
    val SheetTitleTopGap = X6
    val SheetGapS = X10
    val SheetGapM = X12
    val SheetGapL = 18.dp // 弹层底部大间隔（原语真实使用，未列入 §4.1）
    val SheetEmptyPaddingV = X8

    // ---------- 语义别名：图片查看器 ----------
    val ViewerChromePadding = X10
    val ViewerDotPaddingEnd = X8
    val ViewerHintPadding = X14
    val ViewerSealInset = X6 // 朱砂细线框内缩

    // ---------- 语义别名：状态原语（加载 / 空态 / 错误 / 重试横幅，M3.4） ----------
    val StateIconSize = X44 // 空态 / 错误态引导图标尺寸（同印章紧凑阈值尺度）
    val StatePaddingV = X34 // 全幅状态块垂直留白（与纸面左留白同尺度）
    val StateGapM = X12 // 状态图标与主文案间距
    val StateGapS = X8 // 主文案与动作按钮间距
    val BannerPaddingH = X14 // 重试横幅水平内边距（对齐卡片内边距）
    val BannerPaddingV = X12 // 重试横幅垂直内边距

    // ---------- 触控 ----------
    val TouchTargetMin = 48.dp // 最小触控目标（minimumInteractiveComponentSize 兜底）
    val ContentMaxWidth = 720.dp // 宽屏内容最大宽度（设置等页）
}
