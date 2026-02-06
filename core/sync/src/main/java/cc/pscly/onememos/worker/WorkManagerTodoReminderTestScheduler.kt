package cc.pscly.onememos.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import cc.pscly.onememos.domain.sync.TodoReminderTestScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import androidx.work.workDataOf

/**
 * 基于 WorkManager 的“测试提醒”调度器。
 *
 * 设计：
 * - 只用于验证链路（权限/通道/调度），不依赖 RescheduleWorker 的扫描逻辑
 * - 统一使用 TodoReminderNotifyWorker 展示通知，确保用户看到的是“真实通知效果”
 */
@Singleton
class WorkManagerTodoReminderTestScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TodoReminderTestScheduler {
    override fun requestTest(
        itemId: String,
        dueAtLocal: String,
        delaySeconds: Long,
    ) {
        val id = itemId.trim()
        if (id.isBlank()) return

        val delay = delaySeconds.coerceIn(3L, 60L)

        val request =
            OneTimeWorkRequestBuilder<TodoReminderNotifyWorker>()
                .setInitialDelay(delay, TimeUnit.SECONDS)
                // 尽量快执行；拿不到 quota 就降级成普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TodoReminderNotifyWorker.TAG)
                .setInputData(
                    workDataOf(
                        TodoReminderNotifyWorker.KEY_ITEM_ID to id,
                        TodoReminderNotifyWorker.KEY_DUE_AT_LOCAL to dueAtLocal.trim(),
                        TodoReminderNotifyWorker.KEY_BEFORE_MINUTES to 0,
                    ),
                )
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(itemId = id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun uniqueWorkName(itemId: String): String = "todo_reminder_test:$itemId"
}

