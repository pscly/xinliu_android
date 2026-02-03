package cc.pscly.onememos.domain.model

/**
 * 同步状态（UI 需要直接展示）。
 * - LOCAL_ONLY：纯本地（未绑定服务器，不会自动上传）
 * - DIRTY：已绑定服务器，但本地有变更待上传/同步
 * - SYNCING：同步中（WorkManager 正在处理）
 * - SYNCED：已与服务器同步
 * - FAILED：同步失败（可重试）
 */
enum class SyncStatus {
    LOCAL_ONLY,
    DIRTY,
    SYNCING,
    SYNCED,
    FAILED,
}
