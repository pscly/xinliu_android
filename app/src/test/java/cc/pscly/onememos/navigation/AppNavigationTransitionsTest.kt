package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面转场决策（ADR 0012 / M2.9）的纯逻辑回归：
 * - 系统“移除动画”或设置 `pageTransitionsEnabled=false`（即 reducedMotion）→ 零动画直切
 * - 否则 fade + 共享轴 X：前进向左、返回向右
 */
class AppNavigationTransitionsTest {

    @Test
    fun plan_reducedMotion_forward_cutsToIdentity() {
        val plan = planPageTransition(reducedMotion = true, pop = false)
        assertTrue(plan.reducedMotion)
        assertNull(plan.slideDirection)
    }

    @Test
    fun plan_reducedMotion_pop_cutsToIdentity() {
        val plan = planPageTransition(reducedMotion = true, pop = true)
        assertTrue(plan.reducedMotion)
        assertNull(plan.slideDirection)
    }

    @Test
    fun plan_normal_forward_sharedAxisLeft() {
        val plan = planPageTransition(reducedMotion = false, pop = false)
        assertEquals(PageSlideDirection.Left, plan.slideDirection)
    }

    @Test
    fun plan_normal_pop_sharedAxisRight() {
        val plan = planPageTransition(reducedMotion = false, pop = true)
        assertEquals(PageSlideDirection.Right, plan.slideDirection)
    }
}
