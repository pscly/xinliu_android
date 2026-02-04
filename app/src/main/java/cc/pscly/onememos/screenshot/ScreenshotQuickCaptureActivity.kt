package cc.pscly.onememos.screenshot

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import cc.pscly.onememos.overlay.QuickCaptureOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScreenshotQuickCaptureActivity : ComponentActivity() {
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                finish()
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                runCatching {
                    val uri = captureOneFrameToCache(resultCode = result.resultCode, data = result.data!!)
                    openOverlayWithAttachment(uri)
                }.onFailure { e ->
                    Toast.makeText(this@ScreenshotQuickCaptureActivity, e.message?.take(200) ?: "截图失败", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "截图记录需要先授予“在其他应用上层显示”权限", Toast.LENGTH_LONG).show()
            val uri = Uri.parse("package:$packageName")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
            finish()
            return
        }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private suspend fun captureOneFrameToCache(
        resultCode: Int,
        data: Intent,
    ): Uri {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val projection = mpm.getMediaProjection(resultCode, data)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display =
            projection.createVirtualDisplay(
                "one_memos_screenshot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null,
            )

        try {
            val image =
                waitForImage(reader) ?: throw IllegalStateException("未获取到截图数据")
            try {
                val bitmap = withContext(Dispatchers.Default) { toBitmap(image, width, height) }
                return saveBitmapToCache(bitmap)
            } finally {
                runCatching { image.close() }
            }
        } finally {
            runCatching { display.release() }
            runCatching { reader.close() }
            runCatching { projection.stop() }
        }
    }

    private suspend fun waitForImage(reader: ImageReader): Image? {
        repeat(24) {
            val image = reader.acquireLatestImage()
            if (image != null) return image
            delay(50)
        }
        return null
    }

    private fun toBitmap(
        image: Image,
        width: Int,
        height: Int,
    ): Bitmap {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val dir = File(cacheDir, "screenshots").apply { mkdirs() }
        val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").format(LocalDateTime.now())
        val file = File(dir, "screenshot-$ts.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun openOverlayWithAttachment(uri: Uri) {
        startService(
            Intent(this, QuickCaptureOverlayService::class.java)
                .setAction(QuickCaptureOverlayService.ACTION_SCREENSHOT_CAPTURE)
                .putStringArrayListExtra(
                    QuickCaptureOverlayService.EXTRA_ATTACHMENTS,
                    arrayListOf(uri.toString()),
                ),
        )
    }
}
