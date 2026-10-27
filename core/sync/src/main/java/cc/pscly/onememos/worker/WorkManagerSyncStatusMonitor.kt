package cc.pscly.onememos.worker

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.SyncWorkState
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncStatusMonitor @Inject constructor(
    @ApplicationContext context: Context,
    memoDao: MemoDao,
    settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
) : SyncStatusMonitor {
    private val workManager = WorkManager.getInstance(context)

    /**
     * 一次性同步任务的工作信息。
     *
     * 注意（1.17.5 修复）：WorkManager 会自动为每个 Worker 追加一条“类名 tag”
     * （`cc.pscly.onememos.worker.MemosSyncWorker`），而**周期同步**复用同一个 Worker 类。
     * 因此按 [MemosSyncWorker.TAG] 或类名查询都会把周期任务（长期处于 ENQUEUED）算进来，
     * 导致 UI 永远显示“同步中…”。
     *
     * 现在只按**一次性任务的 unique name**（[MemosSyncWorker.UNIQUE_WORK_NAME]）查询，
     * 并过滤掉周期任务，保证“同步中”只反映真正的一次性同步。
     */
    private val workInfos: Flow<List<WorkInfo>> =
        callbackFlow {
            val liveData = workManager.getWorkInfosForUniqueWorkLiveData(MemosSyncWorker.UNIQUE_WORK_NAME)
            val observer =
                Observer<List<WorkInfo>> { infos ->
                    trySend(infos.filterNot { it.isPeriodicSyncWork() }).isSuccess
                }
            liveData.observeForever(observer)
            awaitClose { liveData.removeObserver(observer) }
        }
            .conflate()
            .distinctUntilChanged()

    /** 周期同步（one_memos_periodic_sync）不计入“同步中”状态。 */
    private fun WorkInfo.isPeriodicSyncWork(): Boolean = isPeriodicSyncTags(tags)

    private val pendingCount: Flow<Int> =
        memoDao
            .observeMemosNeedingSyncCount()
            .distinctUntilChanged()

    private val lastSync = settingsRepository.settings.map { it.lastSync }.distinctUntilChanged()

    override val globalState: Flow<GlobalSyncState> =
        combine(
            workInfos,
            pendingCount,
            networkMonitor.isOnline,
            lastSync,
        ) { infos, pending, online, last ->
            GlobalSyncState(
                workState = computeWorkState(infos),
                pendingCount = pending.coerceAtLeast(0),
                networkOnline = online,
                lastSuccessAt = last.lastSuccessAt,
                lastError = last.lastError,
                lastErrorAt = last.lastErrorAt,
                lastErrorHttpCode = last.lastErrorHttpCode,
            )
        }.distinctUntilChanged()

    private fun computeWorkState(infos: List<WorkInfo>): SyncWorkState {
        if (infos.any { it.state == WorkInfo.State.RUNNING }) return SyncWorkState.RUNNING
        if (infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) return SyncWorkState.ENQUEUED
        return SyncWorkState.IDLE
    }
}

/**
 * 判断一组 WorkManager tag 是否属于**周期同步**。
 *
 * 回归背景（1.17.5）：WorkManager 会给每个 Worker 自动追加类名 tag，
 * 周期同步与一次性同步共用 `MemosSyncWorker`，因此按 tag/类名查询都会混入周期任务
 * （长期 ENQUEUED），使 UI 永远显示“同步中…”。这里把判定抽成纯函数便于单测。
 */
internal fun isPeriodicSyncTags(tags: Set<String>): Boolean =
    tags.contains(WorkManagerSyncScheduler.PERIODIC_TAG) ||
        tags.contains(WorkManagerSyncScheduler.UNIQUE_PERIODIC_WORK_NAME) ||
        tags.contains(MemosSyncWorker.PERIODIC_MARKER_TAG)
