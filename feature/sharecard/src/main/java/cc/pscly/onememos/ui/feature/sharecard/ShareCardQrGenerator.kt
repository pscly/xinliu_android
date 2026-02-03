package cc.pscly.onememos.ui.feature.sharecard

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 生成二维码 Bitmap（纯本地，不依赖网络）。
 * 说明：仅用于“墨迹卡片”导出，大小/边距固定，避免过多调参。
 */
object ShareCardQrGenerator {
    fun generate(
        text: String,
        sizePx: Int,
    ): Bitmap? {
        val t = text.trim()
        if (t.isBlank() || sizePx <= 0) return null

        return runCatching {
            val hints =
                mapOf(
                    EncodeHintType.MARGIN to 1,
                )
            val matrix = QRCodeWriter().encode(t, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            // 体验优化：避免双层循环 setPixel() 的大量调用开销，改为一次性 setPixels。
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(sizePx * sizePx)
            val black = Color.BLACK
            val white = Color.WHITE
            var offset = 0
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix[x, y]) black else white
                }
                offset += sizePx
            }
            bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            bmp
        }.getOrNull()
    }
}
