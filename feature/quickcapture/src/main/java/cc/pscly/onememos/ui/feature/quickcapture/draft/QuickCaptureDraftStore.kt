package cc.pscly.onememos.ui.feature.quickcapture.draft

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.random.Random

class QuickCaptureDraftStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val atomicWriter: DraftAtomicWriter = DraftAtomicWriter { target, bytes ->
        defaultAtomicWrite(target = target, bytes = bytes)
    },
) {
    private val mutex = Mutex()

    private val draftDir: File = File(context.noBackupFilesDir, DRAFT_DIR_NAME)
    private val draftFile: File = File(draftDir, DRAFT_FILE_NAME)
    private val attachmentsDir: File = File(context.filesDir, ATTACHMENTS_DIR_NAME)

    suspend fun loadDraft(): QuickCaptureDraft? =
        withContext(ioDispatcher) {
            // 避免“主线程持锁 + withContext(IO)”导致的恢复/释放锁依赖主线程，从而在 runBlocking 场景死锁。
            mutex.withLock {
                val raw =
                    runCatching {
                        AtomicFile(draftFile)
                            .openRead()
                            .use { input ->
                                val bytes = input.readBytes()
                                String(bytes, Charsets.UTF_8)
                            }
                    }
                        .getOrNull()
                        ?.trim()
                        .orEmpty()
                if (raw.isBlank()) return@withLock null
                return@withLock runCatching { decodeDraft(json = raw) }.getOrNull()
            }
        }

    suspend fun saveDraft(draft: QuickCaptureDraft) {
        withContext(ioDispatcher) {
            mutex.withLock {
                draftDir.mkdirs()
                attachmentsDir.mkdirs()

                val normalized =
                    draft.copy(
                        schemaVersion = SCHEMA_VERSION,
                        updatedAt = draft.updatedAt.takeIf { it > 0L } ?: nowProvider(),
                        attachments = draft.attachments.filter { it.fileName.isNotBlank() },
                    )
                val json = encodeDraft(draft = normalized)
                atomicWriter.write(target = draftFile, bytes = json.toByteArray(Charsets.UTF_8))

                cleanupOrphanAttachments(keep = normalized.attachments)
            }
        }
    }

    suspend fun clearDraft() {
        withContext(ioDispatcher) {
            mutex.withLock {
                runCatching { draftFile.delete() }
                runCatching { File(draftDir, "$DRAFT_FILE_NAME.bak").delete() }
                runCatching { attachmentsDir.deleteRecursively() }
                Unit
            }
        }
    }

    suspend fun copyInAttachment(uri: Uri): QuickCaptureDraftAttachment =
        withContext(ioDispatcher) {
            mutex.withLock {
                attachmentsDir.mkdirs()
                val resolver = context.contentResolver

                val guessedName =
                    runCatching {
                        resolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                        }
                    }.getOrNull()

                val safeBase =
                    sanitizeFilename(
                        (guessedName ?: "attachment_${nowProvider()}")
                            .trim()
                            .ifBlank { "attachment_${nowProvider()}" },
                    )
                        .take(120)
                        .ifBlank { "attachment_${nowProvider()}" }

                val hasSuffix = safeBase.contains('.') && safeBase.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()
                val suffix =
                    if (hasSuffix) {
                        safeBase.substringAfterLast('.').lowercase(Locale.US)
                    } else {
                        val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase(Locale.US)
                    }

                val random = Random.nextBytes(6).joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
                val outName =
                    if (!suffix.isNullOrBlank() && !safeBase.lowercase(Locale.US).endsWith(".$suffix")) {
                        "${nowProvider()}_${random}_${safeBase}.$suffix"
                    } else {
                        "${nowProvider()}_${random}_$safeBase"
                    }.take(180)

                val outFile = File(attachmentsDir, outName)

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开附件输入流")

                if (!outFile.exists() || outFile.length() <= 0L) {
                    runCatching { outFile.delete() }
                    throw IllegalStateException("附件拷贝失败")
                }

                QuickCaptureDraftAttachment(
                    fileName = outFile.name,
                    originalName = guessedName?.take(200),
                )
            }
        }

    private fun cleanupOrphanAttachments(keep: List<QuickCaptureDraftAttachment>) {
        if (!attachmentsDir.exists() || !attachmentsDir.isDirectory) return
        val keepNames = keep.map { it.fileName }.filter { it.isNotBlank() }.toHashSet()
        attachmentsDir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.name !in keepNames) runCatching { f.delete() }
        }
    }

    private fun encodeDraft(draft: QuickCaptureDraft): String {
        val obj =
            JSONObject().apply {
                put("schemaVersion", draft.schemaVersion)
                put("updatedAt", draft.updatedAt)
                put("text", draft.text)
                val arr = JSONArray()
                draft.attachments.forEach { att ->
                    arr.put(
                        JSONObject().apply {
                            put("fileName", att.fileName)
                            if (!att.originalName.isNullOrBlank()) put("originalName", att.originalName)
                        },
                    )
                }
                put("attachments", arr)
            }
        return obj.toString()
    }

    private fun decodeDraft(json: String): QuickCaptureDraft {
        val obj = JSONObject(json)
        val schema = obj.optInt("schemaVersion", SCHEMA_VERSION)
        val updatedAt = obj.optLong("updatedAt", 0L)
        val text = obj.optString("text", "")

        val attachments = ArrayList<QuickCaptureDraftAttachment>()
        val arr = obj.optJSONArray("attachments")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val fileName = item.optString("fileName", "").trim()
                if (fileName.isBlank()) continue
                val originalName = item.optString("originalName", "").trim().ifBlank { null }
                val f = File(attachmentsDir, fileName)
                if (!f.exists() || !f.isFile) continue
                attachments += QuickCaptureDraftAttachment(fileName = fileName, originalName = originalName)
            }
        }
        return QuickCaptureDraft(
            schemaVersion = schema,
            updatedAt = updatedAt,
            text = text,
            attachments = attachments,
        )
    }

    private fun sanitizeFilename(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '.' || ch == '_' || ch == '-' -> append(ch)
                    else -> append('_')
                }
            }
        }
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private companion object {
        private const val SCHEMA_VERSION = 1

        private const val DRAFT_DIR_NAME = "quick_capture_draft"
        private const val DRAFT_FILE_NAME = "draft.json"
        private const val ATTACHMENTS_DIR_NAME = "quick_capture_draft_attachments"

        private fun defaultAtomicWrite(
            target: File,
            bytes: ByteArray,
        ) {
            target.parentFile?.mkdirs()
            val atomicFile = AtomicFile(target)
            var out: FileOutputStream? = null
            try {
                out = atomicFile.startWrite()
                out.write(bytes)
                runCatching { out.fd.sync() }
                atomicFile.finishWrite(out)
            } catch (e: Exception) {
                if (out != null) {
                    runCatching { atomicFile.failWrite(out) }
                }
                throw e
            }
        }
    }
}

fun interface DraftAtomicWriter {
    fun write(
        target: File,
        bytes: ByteArray,
    )
}
