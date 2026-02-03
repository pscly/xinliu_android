package cc.pscly.onememos.domain.model

data class CacheStats(
    val databaseBytes: Long,
    val imageCacheBytes: Long,
    val attachmentCacheBytes: Long,
    val otherCacheBytes: Long,
) {
    val totalBytes: Long = databaseBytes + imageCacheBytes + attachmentCacheBytes + otherCacheBytes
}
