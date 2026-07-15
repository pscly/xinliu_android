package cc.pscly.onememos.quicktiles

import android.content.Context
import android.content.Intent

interface ScreenshotEntryPort {
    fun activityIntent(context: Context): Intent
}
