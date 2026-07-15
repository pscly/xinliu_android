package cc.pscly.onememos.quicktiles

import android.content.Context
import cc.pscly.onememos.di.QuickCaptureTargetAdapter
import cc.pscly.onememos.di.ScreenshotEntryAdapter
import cc.pscly.onememos.overlay.QuickCaptureOverlayEntryActivity
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuickTileTargetAdaptersTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun quickCaptureAdapter_pointsToAppOwnedActivities() {
        val adapter = QuickCaptureTargetAdapter()
        assertEquals(
            QuickCaptureActivity::class.java.name,
            adapter.activityIntent(context).component?.className,
        )
        assertEquals(
            QuickCaptureOverlayEntryActivity::class.java.name,
            adapter.overlayIntent(context).component?.className,
        )
    }

    @Test
    fun screenshotAdapter_pointsToScreenshotActivity() {
        val adapter = ScreenshotEntryAdapter()
        assertEquals(
            ScreenshotQuickCaptureActivity::class.java.name,
            adapter.activityIntent(context).component?.className,
        )
    }
}
