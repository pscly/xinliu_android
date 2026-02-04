package cc.pscly.onememos.overlay

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 悬浮窗场景无法直接使用 Compose 的 rememberLauncherForActivityResult；
 * 这里用一个透明 Activity 作为“系统选图器”的中转站。
 */
class QuickCaptureOverlayPickImagesActivity : ComponentActivity() {
    private val pickImagesLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                val attachments = ArrayList<String>(uris.size)
                for (uri in uris) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    attachments.add(uri.toString())
                }

                runCatching {
                    startService(
                        Intent(this, QuickCaptureOverlayService::class.java)
                            .setAction(QuickCaptureOverlayService.ACTION_ADD_ATTACHMENTS)
                            .putStringArrayListExtra(QuickCaptureOverlayService.EXTRA_ATTACHMENTS, attachments),
                    )
                }.onFailure { e ->
                    Toast.makeText(this, e.message?.take(200) ?: "无法添加图片", Toast.LENGTH_SHORT).show()
                }
            }

            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 避免因重建导致重复弹出选择器。
        if (savedInstanceState != null) {
            finish()
            return
        }

        pickImagesLauncher.launch(arrayOf("image/*"))
    }
}
