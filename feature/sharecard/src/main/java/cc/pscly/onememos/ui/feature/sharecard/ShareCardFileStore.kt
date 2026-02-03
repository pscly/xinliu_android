package cc.pscly.onememos.ui.feature.sharecard

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareCardFileStore {
    private fun nowStamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(Date())

    fun writeToCacheJpeg(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 92,
    ): File {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        cleanupOldFiles(dir = dir, keep = 30)
        val file = File(dir, "share-card-${nowStamp()}.jpg")
        FileOutputStream(file).use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 98), out)
            if (!ok) throw IllegalStateException("图片编码失败")
        }
        return file
    }

    fun writeToCacheJpegs(
        context: Context,
        bitmaps: List<Bitmap>,
        quality: Int = 92,
    ): List<File> {
        if (bitmaps.isEmpty()) return emptyList()
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        cleanupOldFiles(dir = dir, keep = 60)
        val stamp = nowStamp()
        return bitmaps.mapIndexedNotNull { index, bmp ->
            runCatching {
                val n = (index + 1).toString().padStart(2, '0')
                val file = File(dir, "share-card-${stamp}-p${n}.jpg")
                FileOutputStream(file).use { out ->
                    val ok = bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 98), out)
                    if (!ok) throw IllegalStateException("图片编码失败")
                }
                file
            }.getOrNull()
        }
    }

    /**
     * 保存到相册（Android 10+ 推荐走 MediaStore）。这里不申请额外权限：
     * - 写入时通过 MediaStore 插入，系统会把文件放到 Pictures/1memos
     */
    fun saveToGalleryJpeg(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 92,
    ): Uri? {
        val name = "1memos-share-card-${nowStamp()}.jpg"

        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + "1memos",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

        return try {
            val ok =
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 98), out)
                }
            if (ok != true) throw IllegalStateException("保存失败")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun saveToGalleryJpegs(
        context: Context,
        bitmaps: List<Bitmap>,
        quality: Int = 92,
    ): List<Uri> {
        if (bitmaps.isEmpty()) return emptyList()
        return bitmaps.mapNotNull { bmp -> saveToGalleryJpeg(context, bmp, quality) }
    }

    private fun cleanupOldFiles(dir: File, keep: Int) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.size <= keep) return
        files.drop(keep).forEach { f -> runCatching { f.delete() } }
    }
}
