package cc.pscly.onememos.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerTodoSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TodoSyncScheduler {
    override fun requestSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<FlowTodoSyncWorker>()
                .setConstraints(constraints)
                // Android 14 更容易冻结后台进程：能拿到 quota 时尽快执行；拿不到则自动降级为普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(FlowTodoSyncWorker.TAG)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            FlowTodoSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

