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
    val X18 = 18.dp // CPI 尺寸 / 骨架屏正文行高（SheetGapL 同源）
    val X20 = 20.dp // TagFilterBottomSheet 水平页边距
    val X22 = 22.dp // 分享卡画布边距 / 锦囊分隔
    val X24 = 24.dp // 朱砂竖线距左位置 / 引用条最小高度
    val X26 = 26.dp // 分享卡行距
    val X28 = 28.dp // 附件角标与移除钮
    val X30 = 30.dp // 待办状态圈
    val X34 = 34.dp // 纸面内容左内边距（为竖线保留文字起始空间）
    val X44 = 44.dp // SealIconButton 默认尺寸 / 印章紧凑阈值
    val X54 = 54.dp // 分享卡画布水平内边距
    val X56 = 56.dp // SealButton 默认尺寸
    val X64 = 64.dp // 骨架屏块宽
    val X68 = 68.dp // 编辑器行尾留白
    val X76 = 76.dp // 锦囊单图缩略
    val X80 = 80.dp // 分享卡顶部留白
    val X84 = 84.dp // 附件缩略图边长
    val X88 = 88.dp // Markdown 表格单元格最小宽度
    val X92 = 92.dp // 分享卡印章
    val X108 = 108.dp // 分享卡图
    val X120 = 120.dp // 悬浮层输入框最小高（≈3 行 LinePitch + 纸面留白）
    val X150 = 150.dp // 盖章印记固定尺寸 / 悬浮层输入框最大高（≈4 行 LinePitch + 纸面留白）
    val X320 = 320.dp // 收藏弹窗列表最大高
    val X324 = 324.dp // 历史悬浮层输入框最大高（已退役，保留尺度备查）
    val X360 = 360.dp // 分享卡预览高 / 锦囊弹窗高
    val X380 = 380.dp // 个人中心日历高
    val X420 = 420.dp // 更新弹窗内容最大高
    val X520 = 520.dp // 分享卡引用竖条高 / 待办弹层列表高
    val X600 = 600.dp // 首页双列断点

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
    val SheetGapL = X18 // 弹层底部大间隔（原语真实使用，未列入 §4.1；同源 X18）
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

    // ---------- 语义别名：悬浮速记层（M6） ----------
    val OverlayThumbSize = X84 // 附件缩略图边长
    val OverlayThumbBadgeSize = X28 // 附件角标 / 移除钮尺寸
    /** 可见卡片最大高度占全屏视口的比例（半屏） */
    const val OverlayCardMaxHeightFraction = 0.5f
    val OverlayCardMarginH = X16 // 卡片水平边距（相对视口）
    val OverlayCardMarginV = X16 // 自由带内垂直边距（上下各一份）
    val OverlayInputMinHeight = X120 // 输入框最小高（≈3 行）
    val OverlayInputMaxHeight = X150 // 输入框最大高（≈4 行，超出后内部滚动）

    // ---------- 语义别名：分享卡画布（M6） ----------
    val ShareCardMarginX = X22
    val ShareCardLineGap = X26
    val ShareCardPaddingH = X54
    val ShareCardPaddingV = X56
    val ShareCardSealSize = X92
    val ShareCardImageSize = X108
    val ShareCardQuoteBarHeight = X520
    val ShareCardPreviewHeight = X360
    val ShareCardThemesTopPadding = X80
    val ShareCardElevation = X4 // 画布卡片投影（全库唯一使用投影处）

    // ---------- 语义别名：附件 / 图片缩略（M6） ----------
    val AttachmentThumbSize = X84 // 编辑器附件缩略（与悬浮层同值）
    val SingleImageThumbSize = X76 // 锦囊单图缩略
    val GridImageThumbSize = X88 // 锦囊多图缩略

    // ---------- 语义别名：弹窗 / 弹层（M6） ----------
    val DialogListMaxHeight = X320 // 收藏弹窗列表
    val SheetListMaxHeight = X520 // 待办弹层列表
    val UpdateDialogNotesMaxHeight = X420 // 更新弹窗说明
    val CollectionsDialogMaxHeight = X360 // 锦囊弹窗

    // ---------- 语义别名：骨架屏 / 布局（M6） ----------
    val SkeletonTextLineHeight = X18 // 骨架屏正文行高（一次性占位几何）
    val TwoColumnMinWidth = X600 // 首页双列断点（自 HomeScreen 迁入）
    val ProfileCalendarHeight = X380 // 个人中心日历高
    val EditorRowEndPadding = X68 // 编辑器行尾留白
    val TodoStatusIconSize = X30 // 待办状态圈
    val CalendarCellMin = X44 // 日历单元格最小边长
    val CalendarCellMax = X56 // 日历单元格最大边长
    val CalendarDaySize = X34 // 日历日号内圈

    // ---------- 触控 ----------
    val TouchTargetMin = 48.dp // 最小触控目标（minimumInteractiveComponentSize 兜底）
    val ContentMaxWidth = 720.dp // 宽屏内容最大宽度（设置等页）
}
