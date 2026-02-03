package cc.pscly.onememos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class AttachmentPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val memoDao: MemoDao,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.offlineImagePrefetchEnabled) return Result.success()

        val serverBase = MemosUrls.normalizeServerBase(settings.serverUrl) ?: return Result.success()
        if (settings.token.isBlank()) return Result.success()

        // 约定：0 表示“无限”，真正的启停由开关控制。
        // 为避免极端情况下拉取过多导致耗电/耗流量，这里仍保留一个很大的内部上限。
        val maxMemos =
            if (settings.offlineImagePrefetchMaxMemos <= 0) {
                5_000
            } else {
                settings.offlineImagePrefetchMaxMemos.coerceAtLeast(1)
            }
        val maxImages =
            if (settings.offlineImagePrefetchMaxImages <= 0) {
                5_000
            } else {
                settings.offlineImagePrefetchMaxImages.coerceAtLeast(1)
            }

        val attachmentCacheDir = File(applicationContext.filesDir, "one_memos_attachment_cache")
        val maxMb = settings.attachmentCacheMaxMb
        val maxBytes = if (maxMb > 0) maxMb.toLong() * 1024L * 1024L else Long.MAX_VALUE

        return withContext(Dispatchers.IO) {
            try {
                val recent = memoDao.listRecentActiveMemos(limit = maxMemos)
                var done = 0

                // 只在开始与周期性节点检查目录大小，避免每张图都 walkTopDown()。
                var currentBytes = if (maxMb > 0) dirBytes(attachmentCacheDir) else 0L
                var sinceLastSizeCheck = 0
                if (maxMb > 0 && currentBytes >= maxBytes) {
                    return@withContext Result.success()
                }

                for (m in recent) {
                    if (done >= maxImages) break

                    val memoUuid = m.memo.uuid
                    for (a in m.attachments) {
                        if (done >= maxImages) break
                        if (isUsableFileUri(a.cacheUri)) continue
                        val remoteName = a.remoteName ?: continue
                        val filename = a.filename ?: continue
                        val mime = a.mimeType ?: continue
                        if (!mime.startsWith("image/")) continue

                        val cached =
                            runCatching {
                                cacheRepository.ensureImageAttachmentCached(
                                    serverBase = serverBase,
                                    memoUuid = memoUuid,
                                    remoteName = remoteName,
                                    filename = filename,
                                )
                            }.getOrNull()
                        if (!cached.isNullOrBlank()) {
                            done += 1
                            sinceLastSizeCheck += 1
                            if (maxMb > 0 && sinceLastSizeCheck >= 8) {
                                sinceLastSizeCheck = 0
                                currentBytes = dirBytes(attachmentCacheDir)
                                if (currentBytes >= maxBytes) {
                                    return@withContext Result.success()
                                }
                            }
                        }
                    }
                }
                Result.success()
            } catch (_: Exception) {
                // 预取失败不应阻塞主同步流程；稍后由下一次同步/预取再尝试即可。
                Result.success()
            }
        }
    }

    private fun isUsableFileUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return runCatching {
            val parsed = android.net.Uri.parse(uri)
            if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching false
            val path = parsed.path ?: return@runCatching false
            java.io.File(path).exists()
        }.getOrDefault(false)
    }

    private fun dirBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var sum = 0L
        dir.listFiles()?.forEach { f ->
            sum += if (f.isDirectory) dirBytes(f) else f.length()
        }
        return sum
    }

    companion object {
        const val UNIQUE_WORK_NAME = "one_memos_attachment_prefetch"
        const val TAG = "one_memos_attachment_prefetch"
    }
}
