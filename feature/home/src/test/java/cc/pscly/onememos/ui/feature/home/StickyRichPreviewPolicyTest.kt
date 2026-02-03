package cc.pscly.onememos.ui.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickyRichPreviewPolicyTest {
    @Test
    fun isStickyRichPreviewEnabled_gatesByBuildType() {
        assertFalse(isStickyRichPreviewEnabled(buildType = "debug"))
        assertTrue(isStickyRichPreviewEnabled(buildType = "benchmark"))
        assertTrue(isStickyRichPreviewEnabled(buildType = "release"))
    }

    @Test
    fun stickyPolicy_evictsByLruAndRefreshesOnMarkSticky() {
        val policy = StickyRichPreviewPolicy(initialLimit = 3)
        policy.markSticky("a")
        policy.markSticky("b")
        policy.markSticky("c")

        // overflow -> evict oldest (a)
        policy.markSticky("d")

        assertFalse(policy.isSticky("a"))
        assertTrue(policy.isSticky("b"))
        assertTrue(policy.isSticky("c"))
        assertTrue(policy.isSticky("d"))

        // refresh b then overflow again -> should evict c
        policy.markSticky("b")
        policy.markSticky("e")

        assertFalse(policy.isSticky("c"))
        assertTrue(policy.isSticky("d"))
        assertTrue(policy.isSticky("b"))
        assertTrue(policy.isSticky("e"))
    }

    @Test
    fun stickyPolicy_limitZeroBypasses_and_limitDecreaseTrimsImmediately() {
        val policy = StickyRichPreviewPolicy(initialLimit = 3)
        policy.markSticky("a")
        policy.markSticky("b")
        policy.markSticky("c")
        assertTrue(policy.isSticky("a"))

        // decrease limit -> immediate trim
        policy.limit = 2

        assertFalse(policy.isSticky("a"))
        assertTrue(policy.isSticky("b"))
        assertTrue(policy.isSticky("c"))

        // set to 0 -> clear immediately + markSticky no-op + isSticky always false
        policy.limit = 0
        policy.markSticky("d")

        assertFalse(policy.isSticky("b"))
        assertFalse(policy.isSticky("c"))
        assertFalse(policy.isSticky("d"))

        // negative -> treated as 0
        policy.limit = -1
        policy.limit = 1
        policy.markSticky("d")
        assertTrue(policy.isSticky("d"))
    }

    @Test
    fun stickyPolicy_clear_removesAll() {
        val policy = StickyRichPreviewPolicy(initialLimit = 2)
        policy.markSticky("a")
        policy.markSticky("b")
        assertTrue(policy.isSticky("a"))

        policy.clear()

        assertFalse(policy.isSticky("a"))
        assertFalse(policy.isSticky("b"))
    }
}
