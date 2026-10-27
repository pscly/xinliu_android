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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: SyncWorkManager,
) : SyncScheduler {
    private val arbitration = Mutex()

    override fun requestSync() {
        enqueueSync(ExistingWorkPolicy.KEEP)
    }

    /**
     * 手动重试：用 REPLACE 顶掉处于排队/退避中的旧任务，避免用户点“重试”后毫无反应。
     */
    override fun requestImmediateSync() {
        enqueueSync(ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueSync(policy: ExistingWorkPolicy) {
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
            policy,
            request,
        )

        ensurePeriodicSync()
    }

    override suspend fun requestFullResync(): FullResyncScheduleResult {
        arbitration.lock()
        try {
            val before = workManager.uniqueWorkSnapshot(MemosSyncWorker.UNIQUE_WORK_NAME)

            val unfinishedBefore = before.values.filterNot { it.isFinished }
            when {
                unfinishedBefore.any { MemosSyncWorker.FULL_RESYNC_TAG in it.tags } ->
                    return FullResyncScheduleResult.Duplicate
                unfinishedBefore.isNotEmpty() ->
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

            workManager.enqueueUniqueWork(
                MemosSyncWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            ).commit()

            val after = workManager.uniqueWorkSnapshot(MemosSyncWorker.UNIQUE_WORK_NAME)

            if (request.id in after) {
                ensurePeriodicSync()
                return FullResyncScheduleResult.Accepted(request.id.toString())
            }

            val racedIds = after.keys - before.keys
            check(racedIds.isNotEmpty()) {
                "requestFullResync: KEEP discarded ${request.id}, but no competing unique work was observable in after snapshot"
            }

            val raced = racedIds.map { after.getValue(it) }
            val racedKinds = raced.map { MemosSyncWorker.FULL_RESYNC_TAG in it.tags }.toSet()

            return when (racedKinds) {
                setOf(true) -> FullResyncScheduleResult.Duplicate
                setOf(false) -> FullResyncScheduleResult.Busy
                else -> error("requestFullResync: ambiguous mixed full/normal KEEP contenders for ${request.id}")
            }
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
                // 显式标记周期任务，供状态监控排除（类名 tag 无法区分）
                .addTag(MemosSyncWorker.PERIODIC_MARKER_TAG)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    internal companion object {
        /** 周期同步的 unique name；也被状态监控用来把周期任务排除在“同步中”之外。 */
        internal const val UNIQUE_PERIODIC_WORK_NAME = "one_memos_periodic_sync"

        /** 周期同步的 tag（与 unique name 相同，便于按 tag 清理）。 */
        internal const val PERIODIC_TAG = "one_memos_periodic_sync"
    }
}
