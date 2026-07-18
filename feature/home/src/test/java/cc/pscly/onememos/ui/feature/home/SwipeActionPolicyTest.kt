package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeActionPolicyTest {
    @Test
    fun `默认动作映射 - 右滑加入待办 左滑收藏`() {
        val defaults = AppSettings()
        assertTrue(defaults.swipeEnabled)
        assertEquals(SwipeAction.ADD_TO_TODO, defaults.swipeRightAction)
        assertEquals(SwipeAction.FAVORITE, defaults.swipeLeftAction)

        assertEquals(
            SwipeAction.ADD_TO_TODO,
            SwipeActionPolicy.actionFor(
                direction = HomeSwipeDirection.START_TO_END,
                rightAction = defaults.swipeRightAction,
                leftAction = defaults.swipeLeftAction,
            ),
        )
        assertEquals(
            SwipeAction.FAVORITE,
            SwipeActionPolicy.actionFor(
                direction = HomeSwipeDirection.END_TO_START,
                rightAction = defaults.swipeRightAction,
                leftAction = defaults.swipeLeftAction,
            ),
        )
    }

    @Test
    fun `阈值 - 为卡片宽度的一半 且在合法区间内`() {
        assertEquals(0.5f, SwipeActionPolicy.THRESHOLD_FRACTION)
        assertTrue(SwipeActionPolicy.THRESHOLD_FRACTION > 0f)
        assertTrue(SwipeActionPolicy.THRESHOLD_FRACTION < 1f)
    }

    @Test
    fun `撤销 - 仅归档支持撤销`() {
        assertTrue(SwipeActionPolicy.supportsUndo(SwipeAction.ARCHIVE))
        assertFalse(SwipeActionPolicy.supportsUndo(SwipeAction.ADD_TO_TODO))
        assertFalse(SwipeActionPolicy.supportsUndo(SwipeAction.FAVORITE))
        assertFalse(SwipeActionPolicy.supportsUndo(SwipeAction.PIN))
    }

    @Test
    fun `设置映射 - 自定义左右滑动作生效`() {
        val settings =
            AppSettings(
                swipeRightAction = SwipeAction.ARCHIVE,
                swipeLeftAction = SwipeAction.PIN,
            )
        assertEquals(
            SwipeAction.ARCHIVE,
            SwipeActionPolicy.actionFor(
                direction = HomeSwipeDirection.START_TO_END,
                rightAction = settings.swipeRightAction,
                leftAction = settings.swipeLeftAction,
            ),
        )
        assertEquals(
            SwipeAction.PIN,
            SwipeActionPolicy.actionFor(
                direction = HomeSwipeDirection.END_TO_START,
                rightAction = settings.swipeRightAction,
                leftAction = settings.swipeLeftAction,
            ),
        )
    }

    @Test
    fun `总开关 - 关闭后禁用手势 回退纯长按`() {
        assertFalse(
            SwipeActionPolicy.gesturesEnabled(
                swipeEnabled = false,
                selectionMode = false,
                mode = HomeScreenMode.ACTIVE,
            ),
        )
    }

    @Test
    fun `总开关 - 多选模式中禁用手势`() {
        assertFalse(
            SwipeActionPolicy.gesturesEnabled(
                swipeEnabled = true,
                selectionMode = true,
                mode = HomeScreenMode.ACTIVE,
            ),
        )
    }

    @Test
    fun `总开关 - 已归档页禁用手势`() {
        assertFalse(
            SwipeActionPolicy.gesturesEnabled(
                swipeEnabled = true,
                selectionMode = false,
                mode = HomeScreenMode.ARCHIVED,
            ),
        )
    }

    @Test
    fun `总开关 - 全部条件满足时启用手势`() {
        assertTrue(
            SwipeActionPolicy.gesturesEnabled(
                swipeEnabled = true,
                selectionMode = false,
                mode = HomeScreenMode.ACTIVE,
            ),
        )
    }

    @Test
    fun `动作池 - 四个动作都有非空中文标签`() {
        SwipeAction.entries.forEach { action ->
            assertTrue(SwipeActionPolicy.label(action).isNotBlank())
        }
    }
}
