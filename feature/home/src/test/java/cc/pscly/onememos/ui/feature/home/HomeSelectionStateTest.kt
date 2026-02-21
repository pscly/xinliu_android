package cc.pscly.onememos.ui.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSelectionStateTest {
    @Test
    fun `初始状态为空 且不在多选模式`() {
        val state = HomeSelectionState()

        assertEquals(emptySet<String>(), state.selectedIds)
        assertFalse(state.selectionMode)
    }

    @Test
    fun `enter 选中 1 条并进入多选模式`() {
        val state = HomeSelectionState().enter("a")

        assertEquals(setOf("a"), state.selectedIds)
        assertTrue(state.selectionMode)
    }

    @Test
    fun `start 是 enter 的别名`() {
        val state = HomeSelectionState().start("a")

        assertEquals(setOf("a"), state.selectedIds)
        assertTrue(state.selectionMode)
    }

    @Test
    fun `enter 遇到空白 id 需要防御 不崩溃 且 no-op`() {
        val original = HomeSelectionState()
        val state = original.enter("   ")

        assertEquals(original, state)
        assertEquals(emptySet<String>(), state.selectedIds)
        assertFalse(state.selectionMode)
    }

    @Test
    fun `toggle 在未选中时加入 在已选中时移除`() {
        val a = "a"
        val b = "b"

        val state1 = HomeSelectionState().toggle(a)
        assertEquals(setOf(a), state1.selectedIds)
        assertTrue(state1.selectionMode)

        val state2 = state1.toggle(b)
        assertEquals(setOf(a, b), state2.selectedIds)
        assertTrue(state2.selectionMode)

        val state3 = state2.toggle(a)
        assertEquals(setOf(b), state3.selectedIds)
        assertTrue(state3.selectionMode)

        val state4 = state3.toggle(b)
        assertEquals(emptySet<String>(), state4.selectedIds)
        assertFalse(state4.selectionMode)
    }

    @Test
    fun `toggle 遇到空白 id 需要防御 不崩溃 且 no-op`() {
        val original = HomeSelectionState(selectedIds = setOf("a"))
        val state = original.toggle("")

        assertEquals(original, state)
        assertEquals(setOf("a"), state.selectedIds)
        assertTrue(state.selectionMode)
    }

    @Test
    fun `clear 清空并退出 多选模式`() {
        val state = HomeSelectionState(selectedIds = setOf("a", "b")).clear()

        assertEquals(emptySet<String>(), state.selectedIds)
        assertFalse(state.selectionMode)
    }

    @Test
    fun `exit 是 clear 的别名`() {
        val state = HomeSelectionState(selectedIds = setOf("a")).exit()

        assertEquals(emptySet<String>(), state.selectedIds)
        assertFalse(state.selectionMode)
    }
}
