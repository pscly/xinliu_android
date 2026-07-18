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
 *
 * 组件只引用本对象的语义形状，不再书写 RoundedCornerShape(裸值)。
 */
object InkShape {
    // ---------- 圆角尺度 ----------
    val RadiusL = 14.dp
    val RadiusM = 12.dp
    val RadiusS = 10.dp
    val RadiusXs = 2.dp

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

    /**
     * 印章控件圆角规则：尺寸不大于紧凑阈值（44dp）时用 12dp，否则用 14dp。
     * 供 SealButton / SealIconButton 统一调用，避免业务侧复制另一套阈值。
     */
    fun sealFor(size: Dp): RoundedCornerShape =
        if (size <= InkSpacing.SealCompactThreshold) SealCompact else Seal
}
