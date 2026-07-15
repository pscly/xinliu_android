package cc.pscly.onememos.quicktiles

import android.content.Context
import android.content.Intent

interface QuickCaptureTargetPort {
    fun activityIntent(context: Context): Intent

    fun overlayIntent(context: Context): Intent
}
