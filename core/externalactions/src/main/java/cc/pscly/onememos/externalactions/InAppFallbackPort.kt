package cc.pscly.onememos.externalactions

import android.content.Context
import android.content.Intent

interface InAppFallbackPort {
    fun todoIntent(context: Context): Intent
}
