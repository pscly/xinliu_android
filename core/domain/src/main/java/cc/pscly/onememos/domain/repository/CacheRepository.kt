package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.CacheStats

interface CacheRepository {
    suspend fun getCacheStats(): CacheStats

    suspend fun clearImageCache()

    suspend fun clearAttachmentCache()

    suspend fun clearAllCache()

    /**
     * 将远端附件下载到 filesDir 下的持久缓存目录，并把结果写回数据库的 attachment.cacheUri。
     * - 用于“真正本地缓存/离线秒开”
     * - 不会影响服务端数据
     */
    suspend fun ensureImageAttachmentCached(
        serverBase: String,
        memoUuid: String,
        remoteName: String,
        filename: String,
    ): String?
}
