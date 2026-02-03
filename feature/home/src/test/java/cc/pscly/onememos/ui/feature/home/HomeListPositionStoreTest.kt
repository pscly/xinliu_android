package cc.pscly.onememos.ui.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeListPositionStoreTest {
    @Test
    fun `capture then pending is available until marked restored`() {
        val store = HomeListPositionStore()
        assertNull(store.pending())

        store.capture(12, 34)
        assertEquals(12 to 34, store.peek())
        assertEquals(12 to 34, store.pending())

        // 未标记已恢复前，pending 需要持续存在，便于等到 Paging 加载到位后再恢复。
        assertEquals(12 to 34, store.pending())

        store.markRestored()
        assertNull(store.pending())
        assertEquals(12 to 34, store.peek())
    }

    @Test
    fun `capture clamps negative values`() {
        val store = HomeListPositionStore()
        store.capture(-1, -9)
        assertEquals(0 to 0, store.peek())
        assertEquals(0 to 0, store.pending())
    }
}
