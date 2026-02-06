package cc.pscly.onememos.domain.model

/**
 * 可用于 UI 展示的“全局同步状态”。
 *
 * 目标：
 * - 让用户直观看到：是否在同步、是否离线、是否鉴权失效、还有多少条待同步。
 * - 让朋友也能看懂：同步失败时给出清晰可恢复的入口（重试/重新登录/打开网络）。
 */
data class GlobalSyncState(
    val workState: SyncWorkState = SyncWorkState.IDLE,
    val pendingCount: Int = 0,
    val networkOnline: Boolean = true,
    val lastSuccessAt: Long = 0L,
    val lastError: String = "",
    val lastErrorAt: Long = 0L,
    val lastErrorHttpCode: Int = 0,
) {
    val isSyncing: Boolean get() = workState == SyncWorkState.RUNNING

    val isEnqueued: Boolean get() = workState == SyncWorkState.ENQUEUED

    val hasError: Boolean get() = lastError.isNotBlank()

    val authInvalid: Boolean get() = lastErrorHttpCode == 401 || lastErrorHttpCode == 403
}

