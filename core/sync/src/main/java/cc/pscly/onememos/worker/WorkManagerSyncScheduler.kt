package cc.pscly.onememos.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import cc.pscly.onememos.domain.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {
    override fun requestSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<MemosSyncWorker>()
                .setConstraints(constraints)
                // Android 14 更容易冻结后台进程：能拿到 quota 时尽快执行；拿不到则自动降级为普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(MemosSyncWorker.TAG)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MemosSyncWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )

        // 只要用户触发过同步，就启用周期刷新（主要用于“拉取最新服务端记录”）。
        ensurePeriodicSync()
    }

    override fun requestFullResync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<MemosSyncWorker>()
                .setConstraints(constraints)
                // REPLACE 会取消同名任务；worker 侧需协作式取消（目前已在循环内检查 cancellation）。
                .setInputData(workDataOf(MemosSyncWorker.KEY_FORCE_FULL_SYNC to true))
                // Android 14 更容易冻结后台进程：能拿到 quota 时尽快执行；拿不到则自动降级为普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(MemosSyncWorker.TAG)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MemosSyncWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )

        // 手动触发“重新同步所有笔记”也视为一次同步意图，确保周期刷新已启用。
        ensurePeriodicSync()
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
                // 周期同步只做“轻量刷新/上传 pending”，不应触发 full sync。
                .setInputData(workDataOf(MemosSyncWorker.KEY_IS_PERIODIC to true))
                .addTag(PERIODIC_TAG)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            // 使用 UPDATE，确保升级后能把 inputData（KEY_IS_PERIODIC）同步到已存在的周期任务。
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "one_memos_periodic_sync"
        private const val PERIODIC_TAG = "one_memos_periodic_sync"
    }
}
