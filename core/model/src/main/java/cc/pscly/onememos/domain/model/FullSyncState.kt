package cc.pscly.onememos.domain.model

/**
 * 全量同步（Full Sync）执行状态。
 *
 * 说明：该状态用于“手动重同步/全量拉取”，与日常的增量同步区分开。
 */
enum class FullSyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

/**
 * 全量同步阶段。
 *
 * NORMAL：同步正常（未归档）随笔
 * ARCHIVED：同步归档随笔
 */
enum class FullSyncStage {
    NORMAL,
    ARCHIVED,
}

/**
 * 全量同步的持久化状态快照（DataStore）。
 *
 * 字段约定：
 * - lastSuccessAt: epoch millis；0 表示从未成功
 * - syncKey: 用于绑定“当前账号 + 服务器”；不匹配时 UI 侧应视为未完成（IDLE）
 */
data class FullSyncState(
    val status: FullSyncStatus = FullSyncStatus.IDLE,
    val runId: String = "",
    val acknowledgedSuccessRunId: String = "",
    val lastSuccessAt: Long = 0L,
    val lastError: String = "",
    val stage: FullSyncStage = FullSyncStage.NORMAL,
    val pagesFetched: Int = 0,
    val itemsFetched: Int = 0,
    val syncKey: String = "",
)
