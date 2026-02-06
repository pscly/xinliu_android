package cc.pscly.onememos.domain.model

/**
 * 记录“最近一次同步结果”的轻量状态，用于 UI 解释同步/离线/鉴权问题。
 *
 * 说明：
 * - 这里的“同步”指 WorkManager 的同步 Worker（上传 pending + 拉取刷新）。
 * - 该状态与 Full Sync（全量重同步）不同：Full Sync 有自己的进度/阶段模型。
 */
data class LastSyncState(
    val lastSuccessAt: Long = 0L,
    val lastError: String = "",
    val lastErrorAt: Long = 0L,
    val lastErrorHttpCode: Int = 0,
) {
    val hasError: Boolean get() = lastError.isNotBlank()

    val authInvalid: Boolean get() = lastErrorHttpCode == 401 || lastErrorHttpCode == 403
}

