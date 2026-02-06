package cc.pscly.onememos.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 WorkManager 的 Todo 提醒调度器。
 *
 * 设计：
 * - 周期任务：兜底扫描与重排（例如进程被杀/重启、权限变化后）
 * - 立即任务：用户操作后尽快重排，减少“改了提醒但不生效”的窗口
 */
@Singleton
class WorkManagerTodoReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TodoReminderScheduler {
    override fun requestReschedule() {
        val workManager = WorkManager.getInstance(context)

        // 兜底：周期重排。即使用户没再进入待办页，也能保持提醒“最终一致”。
        val periodic =
            PeriodicWorkRequestBuilder<TodoReminderRescheduleWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .addTag(TodoReminderRescheduleWorker.TAG)
                .build()
        workManager.enqueueUniquePeriodicWork(
            TodoReminderRescheduleWorkerUniqueNames.PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )

        // 立刻触发一次重排：REPLACE 可以保证“最新一次用户操作”尽快生效。
        val request =
            OneTimeWorkRequestBuilder<TodoReminderRescheduleWorker>()
                // Android 14 更容易冻结后台进程：能拿到 quota 时尽快执行；拿不到则自动降级为普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TodoReminderRescheduleWorker.TAG)
                .build()
        workManager.enqueueUniqueWork(
            TodoReminderRescheduleWorkerUniqueNames.ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private object TodoReminderRescheduleWorkerUniqueNames {
        const val PERIODIC = "todo_reminder_reschedule_periodic"
        const val ONE_TIME = "todo_reminder_reschedule_once"
    }

    companion object {
        private const val PERIODIC_INTERVAL_HOURS = 6L
    }
}

