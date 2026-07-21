package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * memo shared-bounds key 纯函数契约：不伪造 SharedTransitionScope。
 */
class MemoSharedTransitionTest {
    @Test
    fun memoSharedContentKey_null_returnsNull() {
        assertNull(memoSharedContentKey(null))
    }

    @Test
    fun memoSharedContentKey_blank_returnsNull() {
        assertNull(memoSharedContentKey(""))
        assertNull(memoSharedContentKey("   "))
    }

    @Test
    fun memoSharedContentKey_uuid_usesMemoPrefix() {
        assertEquals("memo/abc-123", memoSharedContentKey("abc-123"))
        assertEquals("memo/memos/中文", memoSharedContentKey("memos/中文"))
    }

    @Test
    fun memoSharedContentKey_differentUuids_doNotCollide() {
        val a = memoSharedContentKey("uuid-a")
        val b = memoSharedContentKey("uuid-b")
        assertNotEquals(a, b)
        assertEquals("memo/uuid-a", a)
        assertEquals("memo/uuid-b", b)
    }
}
