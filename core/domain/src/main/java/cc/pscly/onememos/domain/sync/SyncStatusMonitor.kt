package cc.pscly.onememos.domain.sync

import cc.pscly.onememos.domain.model.GlobalSyncState
import kotlinx.coroutines.flow.Flow

/**
 * 观察“全局同步状态”（同步中/离线/鉴权失效/待同步数量等）。
 *
 * 说明：
 * - 该接口仅暴露领域层可用的状态模型，避免上层 UI 直接依赖 WorkManager / ConnectivityManager。
 * - 具体实现通常位于 :core:sync（WorkManager + NetworkCallback + Room）。
 */
interface SyncStatusMonitor {
    val globalState: Flow<GlobalSyncState>
}

