package cc.pscly.onememos.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * 用于从系统快捷入口（QS Tile）触发悬浮记录的“透明入口”。
 *
 * 目的：
 * - 让系统下拉面板可被正常收起（TileService 走 startActivityAndCollapse）。
 * - 避免打开一个可见的应用页面；这里只做权限检查与启动 Service。
 */
class QuickCaptureOverlayEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, QuickCaptureOverlayService::class.java))
            finish()
            return
        }

        Toast.makeText(this, "需要先授予“在其他应用上层显示”权限，才能使用悬浮记录", Toast.LENGTH_LONG).show()
        val uri = Uri.parse("package:$packageName")
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
        finish()
    }
}
