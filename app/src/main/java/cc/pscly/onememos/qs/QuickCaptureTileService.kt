package cc.pscly.onememos.qs

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import cc.pscly.onememos.R
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.overlay.QuickCaptureOverlayEntryActivity
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class QuickCaptureTileService : TileService() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

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

        // 锁屏时会先提示解锁；解锁后自动打开极速记录（或悬浮记录）
        unlockAndRun {
            scope.launch {
                val overlayEnabled =
                    withContext(Dispatchers.IO) {
                        settingsRepository.settings.first().quickCaptureOverlayEnabled
                    }

                val intent =
                    when {
                        overlayEnabled && Settings.canDrawOverlays(this@QuickCaptureTileService) -> {
                            Intent(this@QuickCaptureTileService, QuickCaptureOverlayEntryActivity::class.java)
                        }
                        overlayEnabled -> {
                            val uri = Uri.parse("package:${packageName}")
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                        }
                        else -> {
                            Intent(this@QuickCaptureTileService, QuickCaptureActivity::class.java)
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
