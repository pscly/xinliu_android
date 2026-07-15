package cc.pscly.onememos.data.cache

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.network.MemosUrls
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import cc.pscly.onememos.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Singleton
@OptIn(ExperimentalCoilApi::class)
class CacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val okHttpClient: OkHttpClient,
    private val memoDao: MemoDao,
    private val settingsRepository: SettingsRepository,
) : CacheRepository {
    private val imageDiskCacheDir = File(context.cacheDir, "one_memos_image_cache")
    private val attachmentCacheRootDir = File(context.filesDir, "one_memos_attachment_cache")

    // 附件缓存 trim 可能被高频触发；用“估算 + 时间/次数节流 + slack + 到期强制收敛”降低全量扫描/排序频率。
    private val attachmentTrimLock = Any()
    @Volatile private var lastAttachmentTrimAtMs: Long = 0L
    @Volatile private var attachmentTrimSinceLastScan: Int = 0
    @Volatile private var attachmentCacheBytesEstimate: Long = -1L
    private val attachmentTrimMinIntervalMs: Long = 2_000L
    private val attachmentTrimHardIntervalMs: Long = 30_000L
    private val attachmentTrimMinCallsBeforeScan: Int = 8
    private val attachmentTrimSlackBytes: Long = 16L * 1024L * 1024L

    override suspend fun getCacheStats(): CacheStats =
        withContext(Dispatchers.IO) {
            val dbBytes = databaseBytes()
            val imageBytes = dirBytes(imageDiskCacheDir)
            val attachmentBytes = dirBytes(attachmentCacheRootDir)
            val cacheTotal = dirBytes(context.cacheDir)
            val otherBytes = (cacheTotal - imageBytes).coerceAtLeast(0L)
            CacheStats(
                databaseBytes = dbBytes,
                imageCacheBytes = imageBytes,
                attachmentCacheBytes = attachmentBytes,
                otherCacheBytes = otherBytes,
            )
        }

    override suspend fun clearImageCache() =
        clearStorage("清理图片缓存") {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            recreateDirectory(imageDiskCacheDir)
        }

    override suspend fun clearAttachmentCache() =
        clearStorage("清理附件缓存") {
            recreateDirectory(attachmentCacheRootDir)
            memoDao.clearAllAttachmentCacheUris()
            synchronized(attachmentTrimLock) {
                attachmentCacheBytesEstimate = 0L
                attachmentTrimSinceLastScan = 0
                lastAttachmentTrimAtMs = 0L
            }
        }

    override suspend fun clearAllCache() =
        clearStorage("清理全部缓存") {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()

            // cacheDir 只包含可安全清理的临时文件；不会触及数据库/设置等持久数据。
            context.cacheDir.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
            ensureDirectory(context.cacheDir)

            // “清理全部缓存”也应该覆盖附件持久缓存（filesDir），否则用户很难理解为什么还占空间。
            recreateDirectory(attachmentCacheRootDir)
            memoDao.clearAllAttachmentCacheUris()
            synchronized(attachmentTrimLock) {
                attachmentCacheBytesEstimate = 0L
                attachmentTrimSinceLastScan = 0
                lastAttachmentTrimAtMs = 0L
            }
        }

    override suspend fun ensureImageAttachmentCached(
        serverBase: String,
        memoUuid: String,
        remoteName: String,
        filename: String,
    ): String? =
        withContext(Dispatchers.IO) {
            // 仅缓存“完整图”，缩略图交给 Coil 生成；这样既能离线秒开，又不引入双份缓存字段。
            val url =
                MemosUrls.attachmentFileUrl(
                    base = serverBase,
                    attachmentName = remoteName,
                    filename = filename,
                    thumbnail = false,
                )

            val serverKey = sha1Hex(serverBase).take(12)
            val dir = File(attachmentCacheRootDir, serverKey).apply { mkdirs() }

            val remoteKey =
                remoteName.substringAfterLast('/').ifBlank { sha1Hex(remoteName).take(12) }
            val safeFilename = sanitizeFilename(filename)
            val outNameRaw =
                when {
                    safeFilename.isBlank() -> remoteKey
                    else -> "${remoteKey}_$safeFilename"
                }
            val outName = outNameRaw.take(140) // 避免极端长文件名导致部分机型/文件系统问题
            val outFile = File(dir, outName)

            val outUri = Uri.fromFile(outFile).toString()
            if (outFile.exists() && outFile.length() > 0L) {
                memoDao.updateAttachmentCacheUri(memoUuid = memoUuid, remoteName = remoteName, cacheUri = outUri)
                return@withContext outUri
            }

            val request = Request.Builder().url(url).get().build()
            val response = runCatching { okHttpClient.newCall(request).execute() }.getOrNull() ?: return@withContext null

            response.use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null

                val tmp = File(dir, "$outName.download")
                runCatching { tmp.delete() }
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (!tmp.exists() || tmp.length() <= 0L) {
                    runCatching { tmp.delete() }
                    return@withContext null
                }

                runCatching { outFile.delete() }
                val moved =
                    runCatching { tmp.renameTo(outFile) }.getOrDefault(false)
                if (!moved) {
                    // 某些文件系统/机型 renameTo 可能失败，做一次拷贝兜底
                    runCatching { tmp.copyTo(outFile, overwrite = true) }
                    runCatching { tmp.delete() }
                }
            }

            if (!outFile.exists() || outFile.length() <= 0L) return@withContext null
            memoDao.updateAttachmentCacheUri(memoUuid = memoUuid, remoteName = remoteName, cacheUri = outUri)

            // 用新增文件大小做估算，尽量避免频繁全量扫描。
            val addedBytes = runCatching { outFile.length() }.getOrDefault(0L)

            val maxMb = runCatching { settingsRepository.settings.first().attachmentCacheMaxMb }.getOrDefault(1024)
            val maxBytes = if (maxMb <= 0) Long.MAX_VALUE else maxMb.toLong() * 1024L * 1024L
            trimAttachmentCacheIfNeeded(maxBytes = maxBytes, addedBytes = addedBytes)
            outUri
        }

    private fun databaseBytes(): Long {
        // 数据库通常由 1 个主文件 + -wal/-shm 组成；这里把目录里所有 databaseList 相关文件都算上。
        val names = runCatching { context.databaseList().toList() }.getOrElse { emptyList() }
        if (names.isEmpty()) return 0L

        var sum = 0L
        for (name in names) {
            sum += fileBytes(context.getDatabasePath(name))
            sum += fileBytes(context.getDatabasePath("$name-wal"))
            sum += fileBytes(context.getDatabasePath("$name-shm"))
        }
        return sum
    }

    private suspend fun clearStorage(
        operation: String,
        block: suspend () -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                throw error
            } catch (error: Exception) {
                throw IOException("${operation}失败", error)
            }
        }

    private fun recreateDirectory(directory: File) {
        deleteRecursively(directory)
        ensureDirectory(directory)
    }

    private fun deleteRecursively(file: File) {
        if (!file.deleteRecursively()) {
            throw IOException("无法删除缓存路径：${file.absolutePath}")
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("无法创建缓存路径：${directory.absolutePath}")
        }
    }

    private fun fileBytes(file: File?): Long = if (file != null && file.exists() && file.isFile) file.length() else 0L

    private fun dirBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var sum = 0L
        dir.listFiles()?.forEach { f ->
            sum += if (f.isDirectory) dirBytes(f) else f.length()
        }
        return sum
    }

    private suspend fun trimAttachmentCacheIfNeeded(
        maxBytes: Long,
        addedBytes: Long = 0L,
    ) {
        if (maxBytes <= 0L || maxBytes == Long.MAX_VALUE) return
        if (!attachmentCacheRootDir.exists()) return

        val now = SystemClock.elapsedRealtime()
        synchronized(attachmentTrimLock) {
            attachmentTrimSinceLastScan += 1
            if (attachmentCacheBytesEstimate >= 0L && addedBytes > 0L) {
                attachmentCacheBytesEstimate = (attachmentCacheBytesEstimate + addedBytes).coerceAtLeast(0L)
            }

            val elapsed = now - lastAttachmentTrimAtMs
            val hardDue = lastAttachmentTrimAtMs != 0L && elapsed >= attachmentTrimHardIntervalMs
            val minIntervalOk = elapsed >= attachmentTrimMinIntervalMs
            val estimate = attachmentCacheBytesEstimate

            // 超限判定：允许短暂 slack，但 hard interval 到期后仍会强制收敛到 <= max。
            if (estimate >= 0L) {
                if (estimate <= maxBytes) return
                val withinSlack = estimate <= maxBytes + attachmentTrimSlackBytes
                if (withinSlack && !hardDue) return
                val overSlack = !withinSlack
                if (!hardDue) {
                    if (!minIntervalOk) return
                    if (!overSlack && attachmentTrimSinceLastScan < attachmentTrimMinCallsBeforeScan) return
                }
            } else {
                // 无估算值：仅按“时间+次数”做周期性扫描，避免每次都 walkTopDown。
                val singleFileOverLimit = addedBytes > maxBytes
                if (singleFileOverLimit) {
                    if (!minIntervalOk) return
                } else
                if (!hardDue) {
                    if (!minIntervalOk) return
                    if (attachmentTrimSinceLastScan < attachmentTrimMinCallsBeforeScan) return
                }
            }

            lastAttachmentTrimAtMs = now
            attachmentTrimSinceLastScan = 0
        }

        // 单遍历：同时统计 total，并收集 (file,lastModified,length,remoteId)；避免先 dirBytes 再 walkTopDown 再 length 的重复 IO。
        data class AttachmentCacheEntry(
            val file: File,
            val lastModified: Long,
            val length: Long,
            val remoteId: String,
        )

        var total = 0L
        val entries = ArrayList<AttachmentCacheEntry>()
        attachmentCacheRootDir
            .walkTopDown()
            .forEach { f ->
                if (!f.isFile) return@forEach
                val len = runCatching { f.length() }.getOrDefault(0L)
                total += len
                entries +=
                    AttachmentCacheEntry(
                        file = f,
                        lastModified = runCatching { f.lastModified() }.getOrDefault(0L),
                        length = len,
                        remoteId = f.name.substringBefore('_', missingDelimiterValue = f.name).trim(),
                    )
            }

        synchronized(attachmentTrimLock) {
            attachmentCacheBytesEstimate = total
        }
        if (total <= maxBytes) return
        entries.sortBy { it.lastModified }

        for (e in entries) {
            if (total <= maxBytes) break
            runCatching { e.file.delete() }
            total = (total - e.length).coerceAtLeast(0L)
            if (e.remoteId.isNotBlank()) {
                // 即使不清理 DB，UI 也会以“文件是否存在”为准；这里额外清一把，减少脏数据。
                runCatching { memoDao.clearAttachmentCacheUriByRemoteId(e.remoteId) }
            }
        }

        synchronized(attachmentTrimLock) {
            attachmentCacheBytesEstimate = total
        }
    }

    private fun sanitizeFilename(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        // 只保留常见安全字符，避免不同文件系统/机型对特殊字符的兼容问题。
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

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                append(((b.toInt() ushr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
    }
}
