package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.GlobalSyncState

/**
 * 同步状态横幅的动作（与回调解耦，便于 JVM 单测）。
 * 由 HomeScreen 负责把动作映射到具体回调（打开登录页 / 触发同步）。
 */
enum class SyncBannerAction {
    /** 鉴权失效：引导用户重新登录。 */
    OPEN_AUTH,

    /** 非鉴权错误：提示用户重试同步。 */
    RETRY_SYNC,

    /** 有待同步记录且在线、当前未在同步：提供手动同步入口。 */
    SYNC_PENDING,

    /** 其他状态（同步中 / 离线 / 仅排队 / 空闲）：不展示动作。 */
    NONE,
}

/**
 * 同步状态横幅动作策略（纯逻辑，无 Compose 依赖）：
 * 优先级与 HomeScreen.SyncStatusBanner 原有行为保持一致：
 * 鉴权失效 → 打开登录；有错误 → 重试；待同步且在线且未同步 → 同步；否则无动作。
 */
object SyncBannerPolicy {
    /** 根据全局同步状态求横幅应展示的动作。 */
    fun actionFor(state: GlobalSyncState): SyncBannerAction =
        when {
            state.authInvalid -> SyncBannerAction.OPEN_AUTH
            state.hasError -> SyncBannerAction.RETRY_SYNC
            state.pendingCount > 0 && state.networkOnline && !state.isSyncing ->
                SyncBannerAction.SYNC_PENDING
            else -> SyncBannerAction.NONE
        }

    /** 动作的中文展示名（NONE 时沿用 InkRetryBanner 默认兜底“重试”）。 */
    fun label(action: SyncBannerAction): String =
        when (action) {
            SyncBannerAction.OPEN_AUTH -> "去登录"
            SyncBannerAction.RETRY_SYNC -> "重试"
            SyncBannerAction.SYNC_PENDING -> "同步"
            SyncBannerAction.NONE -> "重试"
        }
}
