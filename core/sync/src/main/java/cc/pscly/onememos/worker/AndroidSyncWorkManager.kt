package cc.pscly.onememos.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSyncWorkManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncWorkManager {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun unfinishedWork(uniqueWorkName: String): List<SyncWorkInfo> {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).await()
        return workInfos
            .filter { !it.state.isFinished }
            .map { info -> SyncWorkInfo(id = info.id, tags = info.tags) }
    }

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): SyncEnqueueOperation {
        val operation = workManager.enqueueUniqueWork(uniqueWorkName, policy, request)
        return SyncEnqueueOperation { operation.await() }
    }

    override suspend fun containsWork(id: UUID): Boolean {
        return try {
            val info = workManager.getWorkInfoById(id).await()
            info != null && !info.state.isFinished
        } catch (_: Exception) {
            false
        }
    }

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        workManager.enqueueUniquePeriodicWork(uniqueWorkName, policy, request)
    }
}
