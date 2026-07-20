package cc.pscly.onememos.ui.feature.home

import androidx.compose.ui.unit.Dp
import cc.pscly.onememos.ui.theme.InkSpacing

/**
 * 首页 FAB 组的底部避让计算。
 *
 * 背景：LazyColumn / LazyVerticalStaggeredGrid 的 contentPadding 原先只有 verticalPad，
 * 导致最后一条 memo 的标签行会被悬浮 FAB 组（回到顶部 SealIconButton + 「记」SealButton）遮挡。
 * 这里按 FAB 组的实际布局高度计算列表底部需要预留的净空，全部为 InkSpacing 令牌：
 * - 显示回到顶部时：SealIconButton 外层最小触控高度 TouchTargetMin(48dp)
 *   + 按钮间距 X10 + SealButton 高度 SealButtonSize(56dp) + 额外缓冲 X16
 * - 不显示回到顶部时：SealButtonSize(56dp) + 额外缓冲 X16
 */
object HomeFabClearance {

    /** 列表底部需要为 FAB 组预留的额外净空（不含原有 verticalPad）。 */
    fun fabBottomClearance(showScrollToTop: Boolean): Dp =
        if (showScrollToTop) {
            InkSpacing.TouchTargetMin + InkSpacing.X10 + InkSpacing.SealButtonSize + InkSpacing.X16
        } else {
            InkSpacing.SealButtonSize + InkSpacing.X16
        }
}
