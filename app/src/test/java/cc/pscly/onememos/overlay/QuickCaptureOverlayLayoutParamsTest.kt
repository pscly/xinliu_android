package cc.pscly.onememos.overlay

import android.app.Application
import android.view.WindowInsets
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 锁定悬浮速记 LayoutParams：HyperOS overlay 必须显式 fit IME，
 * 否则 ADJUST_RESIZE 与 WindowInsets.ime 双信号均失效。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickCaptureOverlayLayoutParamsTest {
    @Test
    fun `overlay LayoutParams 含 IME 且保持 ADJUST_RESIZE 与 OVERLAY 类型`() {
        val params = buildQuickCaptureOverlayLayoutParams()

        assertTrue(
            "fitInsetsTypes 必须包含 IME（HyperOS 默认不含）",
            (params.fitInsetsTypes and WindowInsets.Type.ime()) != 0,
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            params.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
        )
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
    }
}
