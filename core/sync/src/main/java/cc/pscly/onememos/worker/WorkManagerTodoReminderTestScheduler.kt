package cc.pscly.onememos.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import cc.pscly.onememos.domain.sync.TodoReminderTestScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

        val delay = delaySeconds.coerceIn(0L, 60L)

        val builder =
            OneTimeWorkRequestBuilder<TodoReminderNotifyWorker>()
                .addTag(TodoReminderNotifyWorker.TAG)
                .setInputData(
                    workDataOf(
                        TodoReminderNotifyWorker.KEY_ITEM_ID to id,
                        TodoReminderNotifyWorker.KEY_DUE_AT_LOCAL to dueAtLocal.trim(),
                        TodoReminderNotifyWorker.KEY_BEFORE_MINUTES to 0,
                    ),
                )

        // 注意：WorkManager 规定 “Expedited” 任务不能设置 initialDelay。
        // 之前为了“尽量快执行”同时设置了 setInitialDelay + setExpedited，会直接抛异常导致主线程崩溃：
        // IllegalArgumentException: Expedited jobs cannot be delayed
        if (delay == 0L) {
            // 尽量快执行；拿不到 quota 就降级成普通任务
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else {
            builder.setInitialDelay(delay, TimeUnit.SECONDS)
        }

        val request = builder.build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(itemId = id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }.onFailure { e ->
            // 测试功能不应导致应用崩溃；这里降级为“失败即忽略”，必要时通过 logcat 追踪。
            Log.e("todo_reminder_test", "enqueue 测试提醒失败：itemId=$id delay=$delay", e)
        }
    }

    private fun uniqueWorkName(itemId: String): String = "todo_reminder_test:$itemId"
}
