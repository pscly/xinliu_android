package cc.pscly.onememos.ui.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 设置「页面转场」派生减少动效（ADR 0012）：
 * pageTransitionsEnabled=false 必须等效于偏好减少动效，
 * 由 [ReducedMotion.Local] 承载并与系统“移除动画”共同门控印章与页面转场。
 */
class ReducedMotionTest {

    @Test
    fun providesFromPageTransitions_enabled_mapsToNotReduced() {
        val provided = ReducedMotion.providesFromPageTransitions(pageTransitionsEnabled = true)
        assertSame(ReducedMotion.Local, provided.compositionLocal)
        assertEquals(false, provided.value)
    }

    @Test
    fun providesFromPageTransitions_disabled_mapsToReduced() {
        val provided = ReducedMotion.providesFromPageTransitions(pageTransitionsEnabled = false)
        assertSame(ReducedMotion.Local, provided.compositionLocal)
        assertEquals(true, provided.value)
    }
}
