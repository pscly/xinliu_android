package cc.pscly.onememos.quicktiles

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidOverlayPermissionGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : OverlayPermissionGateway {
    override val packageName: String
        get() = context.packageName

    override fun isGranted(): Boolean = Settings.canDrawOverlays(context)
}
