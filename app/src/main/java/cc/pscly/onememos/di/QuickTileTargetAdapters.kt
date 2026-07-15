package cc.pscly.onememos.di

import android.content.Context
import android.content.Intent
import cc.pscly.onememos.overlay.QuickCaptureOverlayEntryActivity
import cc.pscly.onememos.quicktiles.QuickCaptureTargetPort
import cc.pscly.onememos.quicktiles.ScreenshotEntryPort
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickCaptureTargetAdapter @Inject constructor() : QuickCaptureTargetPort {
    override fun activityIntent(context: Context): Intent =
        Intent(context, QuickCaptureActivity::class.java)

    override fun overlayIntent(context: Context): Intent =
        Intent(context, QuickCaptureOverlayEntryActivity::class.java)
}

@Singleton
class ScreenshotEntryAdapter @Inject constructor() : ScreenshotEntryPort {
    override fun activityIntent(context: Context): Intent =
        Intent(context, ScreenshotQuickCaptureActivity::class.java)
}
