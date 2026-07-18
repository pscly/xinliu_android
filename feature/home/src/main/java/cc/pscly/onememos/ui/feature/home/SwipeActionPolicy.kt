package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.SwipeAction

/**
 * 滑动手势方向（与 Material3 的 SwipeToDismissBoxValue 解耦，便于 JVM 单测）。
 * LTR 布局下：START_TO_END = 右滑；END_TO_START = 左滑。
 */
enum class HomeSwipeDirection {
    START_TO_END,
    END_TO_START,
}

/**
 * M2.5 滑动手势策略（纯逻辑，无 Compose 依赖）：
 * - 动作池固定为 {加入待办, 收藏(加入锦囊), 归档, 置顶}（见 ADR 0011），左右滑映射由设置页自选。
 * - 归档是唯一支持撤销的动作（Snackbar UNDO）。
 */
object SwipeActionPolicy {
    /**
     * 触发动作的位置阈值：卡片宽度的比例（Material3 默认约 0.5）。
     * 显式声明便于统一与测试，避免各设备密度差异。
     */
    const val THRESHOLD_FRACTION: Float = 0.5f

    /**
     * 是否允许滑动手势：
     * - 总开关关闭 → 回退纯长按（多选）。
     * - 多选模式中 → 禁止滑动，避免与点选冲突。
     * - 已归档页 → 动作池里没有“恢复”，直接禁用滑动。
     */
    fun gesturesEnabled(
        swipeEnabled: Boolean,
        selectionMode: Boolean,
        mode: HomeScreenMode,
    ): Boolean = swipeEnabled && !selectionMode && mode == HomeScreenMode.ACTIVE

    /** 按设置映射求某方向对应的动作。 */
    fun actionFor(
        direction: HomeSwipeDirection,
        rightAction: SwipeAction,
        leftAction: SwipeAction,
    ): SwipeAction =
        when (direction) {
            HomeSwipeDirection.START_TO_END -> rightAction
            HomeSwipeDirection.END_TO_START -> leftAction
        }

    /** 动作是否支持撤销（仅归档，经 Snackbar UNDO 恢复）。 */
    fun supportsUndo(action: SwipeAction): Boolean = action == SwipeAction.ARCHIVE

    /** 动作的中文展示名（滑动背景标签与反馈文案共用）。 */
    fun label(action: SwipeAction): String =
        when (action) {
            SwipeAction.ADD_TO_TODO -> "加入待办"
            SwipeAction.FAVORITE -> "收藏"
            SwipeAction.ARCHIVE -> "归档"
            SwipeAction.PIN -> "置顶"
        }
}
