package cc.pscly.onememos.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cc.pscly.onememos.MainActivity
import cc.pscly.onememos.domain.location.MemoLocationProvider
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.ui.util.QuickInsertTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var locationProvider: MemoLocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 预热定位：分享进来时即可开始取位置，保存时通常已有缓存。
        locationProvider.prime()

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
            val settings = settingsRepository.settings.first()
            val now = System.currentTimeMillis()
            val contentToSave =
                if (settings.autoInsertTimestamp) {
                    QuickInsertTimeFormatter.withLeadingStamp(
                        content = content,
                        epochMillis = now,
                        format = settings.quickInsertTimeFormat,
                    )
                } else {
                    content
                }
            val location = if (settings.autoAppendLocation) locationProvider.latestOrNull() else null
            val uuid =
                memoRepository.createLocalMemo(
                    content = contentToSave,
                    resourceUris = resourceUris,
                    location = location,
                )

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
