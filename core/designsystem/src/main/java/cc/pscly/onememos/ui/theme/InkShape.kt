package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角令牌（语义来源：DESIGN.md §7.2 表面规则）。
 *
 * - 14dp：卡片、纸面与标准印章
 * - 12dp：紧凑印章、筛选片与 Markdown 子表面
 * - 10dp：标签与盖章反馈
 * - 2dp：Markdown 引用竖条
 * - 18dp：骨架卡片 / 横幅（M6）
 * - 8dp：骨架占位 / 画布子面（M6）
 * - 3dp：图例色条（M6）
 *
 * Pill 系列胶囊使用 percent=50 替代历史 RoundedCornerShape(999.dp)，
 * 对尺寸 <200dp 的点元素视觉等价且更精确。
 *
 * 组件只引用本对象的语义形状，不再书写 RoundedCornerShape(裸值)。
 */
object InkShape {
    // ---------- 圆角尺度 ----------
    val RadiusL = 14.dp
    val RadiusM = 12.dp
    val RadiusS = 10.dp
    val RadiusXs = 2.dp
    val RadiusXl = 18.dp // 首页骨架卡片 / 横幅（M6）
    val RadiusXss = 8.dp // 骨架屏占位块、分享卡画布子面（M6）
    val RadiusMicro = 3.dp // 个人中心图例色条（M6）

    // ---------- 语义形状 ----------
    val Card = RoundedCornerShape(RadiusL)
    val Paper = RoundedCornerShape(RadiusL)
    val Seal = RoundedCornerShape(RadiusL)
    val SealCompact = RoundedCornerShape(RadiusM)
    val Chip = RoundedCornerShape(RadiusM)
    val MarkdownSub = RoundedCornerShape(RadiusM) // 代码块、表格等纸面子表面
    val Tag = RoundedCornerShape(RadiusS)
    val Stamp = RoundedCornerShape(RadiusS)
    val QuoteBar = RoundedCornerShape(RadiusXs)
    val Skeleton = RoundedCornerShape(RadiusXss) // 骨架屏占位块
    val CanvasSub = RoundedCornerShape(RadiusXss) // 分享卡画布子面
    val SkeletonCard = RoundedCornerShape(RadiusXl) // 首页骨架卡片 / 横幅
    val Legend = RoundedCornerShape(RadiusMicro) // 图例色条
    /** 胶囊：替代 RoundedCornerShape(999.dp)；使用点元素尺寸均 <200dp，视觉等价 */
    val Pill = RoundedCornerShape(percent = 50)
    val PillStart = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) // 日历连选左端
    val PillEnd = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) // 日历连选右端

    /**
     * 印章控件圆角规则：尺寸不大于紧凑阈值（44dp）时用 12dp，否则用 14dp。
     * 供 SealButton / SealIconButton 统一调用，避免业务侧复制另一套阈值。
     */
    fun sealFor(size: Dp): RoundedCornerShape =
        if (size <= InkSpacing.SealCompactThreshold) SealCompact else Seal
}
