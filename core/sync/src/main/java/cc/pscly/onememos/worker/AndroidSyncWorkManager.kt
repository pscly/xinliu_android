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
import kotlinx.coroutines.flow.first

@Singleton
class AndroidSyncWorkManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncWorkManager {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun uniqueWorkSnapshot(
        uniqueWorkName: String,
    ): Map<UUID, SyncWorkInfo> =
        // WorkManager 2.9.0 公开 API：Flow 一次性取样，避免受限的 ListenableFuture.await()
        workManager
            .getWorkInfosForUniqueWorkFlow(uniqueWorkName)
            .first()
            .associate { info ->
                info.id to SyncWorkInfo(
                    id = info.id,
                    tags = info.tags,
                    isFinished = info.state.isFinished,
                )
            }

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): SyncEnqueueOperation {
        val operation = workManager.enqueueUniqueWork(uniqueWorkName, policy, request)
        return SyncEnqueueOperation { operation.await() }
    }

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        workManager.enqueueUniquePeriodicWork(uniqueWorkName, policy, request)
    }
}
