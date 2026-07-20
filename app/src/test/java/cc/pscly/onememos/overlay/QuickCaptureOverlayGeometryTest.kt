package cc.pscly.onememos.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快速记录悬浮窗几何契约的单元测试（纯 Kotlin，无 Android 依赖）。
 *
 * 双信号约定：
 * - 信号一：窗口被 `SOFT_INPUT_ADJUST_RESIZE` 缩放（`windowHeightPx` 变小）；
 * - 信号二：`WindowInsets.ime.bottom` 到达（`imeBottomPx` 非零）；
 * - 自由带取两者的较小结果，任一信号生效都能把卡片顶到键盘上方，
 *   且双信号同时到达时不得重复扣减。
 */
class QuickCaptureOverlayGeometryTest {

    @Test
    fun `无 IME 时卡片高度受半屏上限约束`() {
        // Given：2000px 全屏视口、窗口满高、无 IME、垂直边距 20px、半屏比例 0.5
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 2000,
            imeBottomPx = 0,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：自由带为满窗口，底部无需避让，卡高被 50% 视口上限截断为 1000
        assertEquals(0, layout.clampedImeBottomPx)
        assertEquals(2000, layout.freeBandHeightPx)
        assertEquals(0, layout.bottomPaddingPx)
        assertEquals(1960, layout.cardAreaHeightPx)
        assertEquals(1000, layout.maxCardHeightPx)
    }

    @Test
    fun `窗口已随键盘缩放时自由带取窗口高度`() {
        // Given：ADJUST_RESIZE 已把窗口缩到 1100px，但 IME insets 未到达（OEM 常见）
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 1100,
            imeBottomPx = 0,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：自由带 = 缩放后的窗口高，底部不再额外避让，卡高仍可到半屏上限
        assertEquals(1100, layout.freeBandHeightPx)
        assertEquals(0, layout.bottomPaddingPx)
        assertEquals(1060, layout.cardAreaHeightPx)
        assertEquals(1000, layout.maxCardHeightPx)
    }

    @Test
    fun `窗口未缩放但 IME insets 到达时自由带取视口减 IME`() {
        // Given：ADJUST_NOTHING 场景或 OEM 不缩放窗口，但 IME insets 到达（900px）
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 2000,
            imeBottomPx = 900,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：自由带 = 视口 - IME = 1100，底部避让 900，卡片整体位于键盘上方
        assertEquals(900, layout.clampedImeBottomPx)
        assertEquals(1100, layout.freeBandHeightPx)
        assertEquals(900, layout.bottomPaddingPx)
        assertEquals(1000, layout.maxCardHeightPx)
        assertTrue(
            "底部避让必须把卡片推入键盘上方的自由带",
            layout.bottomPaddingPx >= layout.clampedImeBottomPx - (2000 - 1100),
        )
    }

    @Test
    fun `双信号同时到达时不重复扣减自由带`() {
        // Given：窗口已缩放到 1100，且 IME insets 也报告 900（同一键盘高度的两种表达）
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 1100,
            imeBottomPx = 900,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：自由带取 min(窗口, 视口 - IME) = 1100，禁止再减一次 900 变成 200
        assertEquals(1100, layout.freeBandHeightPx)
        assertEquals(0, layout.bottomPaddingPx)
        assertEquals(1000, layout.maxCardHeightPx)
    }

    @Test
    fun `自由带小于半屏时卡高由自由带边距后高度决定`() {
        // Given：2000px 视口、窗口满高、IME 高 1400px（自由带仅 600px，小于半屏 1000px）
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 2000,
            imeBottomPx = 1400,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：卡高被自由带（边距后 560px）截断，而不是半屏 1000px
        assertEquals(1400, layout.clampedImeBottomPx)
        assertEquals(600, layout.freeBandHeightPx)
        assertEquals(560, layout.cardAreaHeightPx)
        assertEquals(560, layout.maxCardHeightPx)
    }

    @Test
    fun `超大 IME 被钳制且不产生任何负几何`() {
        // Given：2000px 视口、窗口满高、IME 高 2500px（超过视口）
        val input = QuickCaptureOverlayGeometryInput(
            fullViewportHeightPx = 2000,
            windowHeightPx = 2000,
            imeBottomPx = 2500,
            verticalMarginPx = 20,
            cardMaxHeightFraction = 0.5f,
        )

        // When：计算悬浮窗几何
        val layout = QuickCaptureOverlayGeometry.compute(input)

        // Then：IME 被钳制到视口高度，自由带归零，所有几何量非负
        assertEquals(2000, layout.clampedImeBottomPx)
        assertEquals(0, layout.freeBandHeightPx)
        assertEquals(0, layout.cardAreaHeightPx)
        assertEquals(0, layout.maxCardHeightPx)
        assertTrue(layout.clampedImeBottomPx >= 0)
        assertTrue(layout.bottomPaddingPx >= 0)
        assertTrue(layout.freeBandHeightPx >= 0)
        assertTrue(layout.cardAreaHeightPx >= 0)
        assertTrue(layout.maxCardHeightPx >= 0)
    }
}
