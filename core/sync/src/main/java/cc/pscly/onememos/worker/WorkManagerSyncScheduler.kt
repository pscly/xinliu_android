package cc.pscly.onememos.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import cc.pscly.onememos.domain.sync.SyncScheduler
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: SyncWorkManager,
) : SyncScheduler {
    private val arbitration = Mutex()

    override fun requestSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<MemosSyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(MemosSyncWorker.TAG)
                .build()

        workManager.enqueueUniqueWork(
            MemosSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )

        ensurePeriodicSync()
    }

    override suspend fun requestFullResync(): FullResyncScheduleResult {
        val requestId = UUID.randomUUID().toString()

        arbitration.lock()
        try {
            val unfinished = workManager.unfinishedWork(MemosSyncWorker.UNIQUE_WORK_NAME)
            if (unfinished.any { it.tags.contains(MemosSyncWorker.FULL_RESYNC_TAG) }) {
                return FullResyncScheduleResult.Duplicate
            }
            if (unfinished.isNotEmpty()) {
                return FullResyncScheduleResult.Busy
            }

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<MemosSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(MemosSyncWorker.KEY_FORCE_FULL_SYNC to true))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(MemosSyncWorker.TAG)
                    .addTag(MemosSyncWorker.FULL_RESYNC_TAG)
                    .build()

            val operation = workManager.enqueueUniqueWork(
                MemosSyncWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )

            operation.commit()

            val confirmed = workManager.containsWork(request.id)
            if (!confirmed) return FullResyncScheduleResult.Duplicate

            ensurePeriodicSync()
            return FullResyncScheduleResult.Accepted(requestId)
        } finally {
            arbitration.unlock()
        }
    }

    private fun ensurePeriodicSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<MemosSyncWorker>(
                6,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(MemosSyncWorker.KEY_IS_PERIODIC to true))
                .addTag(PERIODIC_TAG)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "one_memos_periodic_sync"
        private const val PERIODIC_TAG = "one_memos_periodic_sync"
    }
}
