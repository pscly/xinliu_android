package cc.pscly.onememos.qs

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import cc.pscly.onememos.core.quicktiles.R
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.quicktiles.OverlayPermissionGateway
import cc.pscly.onememos.quicktiles.QuickCaptureTargetPort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class QuickCaptureTileService : TileService() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var quickCaptureTargetPort: QuickCaptureTargetPort

    @Inject
    lateinit var overlayPermissionGateway: OverlayPermissionGateway

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.qs_quick_capture)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            scope.launch {
                val overlayEnabled =
                    withContext(Dispatchers.IO) {
                        settingsRepository.settings.first().quickCaptureOverlayEnabled
                    }

                val intent =
                    when {
                        overlayEnabled && overlayPermissionGateway.isGranted() -> {
                            quickCaptureTargetPort.overlayIntent(this@QuickCaptureTileService)
                        }
                        overlayEnabled -> {
                            val uri = Uri.parse("package:${overlayPermissionGateway.packageName}")
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                        }
                        else -> {
                            quickCaptureTargetPort.activityIntent(this@QuickCaptureTileService)
                        }
                    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

                val pendingIntent =
                    PendingIntent.getActivity(
                        this@QuickCaptureTileService,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
