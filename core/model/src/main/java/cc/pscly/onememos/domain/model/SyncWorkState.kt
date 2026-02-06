package cc.pscly.onememos.domain.model

/**
 * 同步 Worker 在 WorkManager 中的大致状态（抽象掉 WorkInfo.State，避免 UI 直接依赖 WorkManager）。
 */
enum class SyncWorkState {
    IDLE,
    ENQUEUED,
    RUNNING,
}

