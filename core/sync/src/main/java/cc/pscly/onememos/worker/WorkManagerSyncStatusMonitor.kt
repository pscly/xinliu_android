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

    private val workInfos: Flow<List<WorkInfo>> =
        callbackFlow {
            val liveData = workManager.getWorkInfosByTagLiveData(MemosSyncWorker.TAG)
            val observer =
                Observer<List<WorkInfo>> { infos ->
                    trySend(infos).isSuccess
                }
            liveData.observeForever(observer)
            awaitClose { liveData.removeObserver(observer) }
        }
            .conflate()
            .distinctUntilChanged()

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
