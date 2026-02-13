package cc.pscly.onememos.domain.collections

fun shouldApplyRemote(
    localClientUpdatedAtMs: Long?,
    localOnly: Boolean,
    remoteClientUpdatedAtMs: Long,
): Boolean {
    if (localOnly) return false
    val localMs = localClientUpdatedAtMs ?: Long.MIN_VALUE
    return remoteClientUpdatedAtMs >= localMs
}

// 与后端 SYNC_MAX_CLIENT_CLOCK_SKEW_SECONDS(300s) 对齐。
private const val MAX_CLIENT_CLOCK_SKEW_MS: Long = 300_000L

fun bumpClientUpdatedAtMs(
    nowMs: Long,
    previousClientUpdatedAtMs: Long,
): Long {
    val maxAllowed = nowMs + MAX_CLIENT_CLOCK_SKEW_MS
    // 本地若出现“时间戳长期在未来”的坏数据，强行夹到上限，避免后续写入永久失效。
    if (previousClientUpdatedAtMs > maxAllowed) return maxAllowed
    return maxOf(nowMs, previousClientUpdatedAtMs + 1L)
}

fun isMoveValid(
    parentById: Map<String, String?>,
    movingFolderId: String,
    targetParentId: String?,
): Boolean {
    if (targetParentId == null) return true
    if (targetParentId == movingFolderId) return false

    // 从目标 parent 往上走到 root，遇到 movingFolderId 说明会形成环。
    val visited = HashSet<String>(16)
    var cur: String? = targetParentId
    while (cur != null) {
        if (!visited.add(cur)) {
            // 数据本身已有环：保守起见视为不可移动。
            return false
        }
        if (cur == movingFolderId) return false
        cur = parentById[cur]
    }
    return true
}

fun reorderIds(
    ids: List<String>,
    fromIndex: Int,
    toIndex: Int,
): List<String> {
    if (fromIndex !in ids.indices) return ids
    if (toIndex !in ids.indices) return ids
    if (fromIndex == toIndex) return ids
    val mutable = ids.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}
