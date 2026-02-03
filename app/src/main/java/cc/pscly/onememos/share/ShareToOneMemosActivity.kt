package cc.pscly.onememos.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cc.pscly.onememos.MainActivity
import cc.pscly.onememos.domain.repository.MemoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * 系统分享入口：
 * - 支持文字、单图、多图（以及其他 stream 文件）
 * - 为了避免分享来的临时 Uri 权限丢失，收到的 stream 会复制到 app 私有目录再写入本地附件列表
 */
@AndroidEntryPoint
class ShareToOneMemosActivity : ComponentActivity() {
    @Inject lateinit var memoRepository: MemoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val share = ShareIntentParser.parse(intent)
            if (share == null) {
                finish()
                return@launch
            }

            val resourceUris =
                withContext(Dispatchers.IO) {
                    share.streamUris
                        .take(MAX_STREAMS)
                        .mapNotNull { uri -> copyStreamToPrivateFile(uri) }
                        .map { it.toString() }
                }

            val content = share.text.trim()
            val uuid = memoRepository.createLocalMemo(content = content, resourceUris = resourceUris)

            val open =
                Intent(this@ShareToOneMemosActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_START_EDITOR_UUID, uuid)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            startActivity(open)
            finish()
        }
    }

    private fun copyStreamToPrivateFile(uri: Uri): Uri? {
        val resolver = contentResolver
        val dir = File(filesDir, "shared").apply { mkdirs() }

        val guessedName =
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            }.getOrNull()

        val safeName =
            (guessedName ?: "shared_${System.currentTimeMillis()}")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120)
                .ifBlank { "shared_${System.currentTimeMillis()}" }

        val suffix =
            safeName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US)
                .takeIf { it.isNotBlank() }

        val outFile =
            if (suffix != null) {
                File(dir, "${System.currentTimeMillis()}_$safeName")
            } else {
                val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                if (!ext.isNullOrBlank()) {
                    File(dir, "${System.currentTimeMillis()}_$safeName.$ext")
                } else {
                    File(dir, "${System.currentTimeMillis()}_$safeName")
                }
            }

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            Uri.fromFile(outFile)
        }.getOrNull()
    }

    private companion object {
        private const val MAX_STREAMS = 12
    }
}
