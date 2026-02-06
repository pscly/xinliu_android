package cc.pscly.onememos.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 系统事件触发的 Todo 提醒重排：
 * - 设备重启后 AlarmManager 闹钟会丢失（EXACT 模式需要重排）
 * - 时间/时区变化会影响本地时间到 epoch 的映射
 * - 应用更新后也可能需要重新调度
 */
@AndroidEntryPoint
class TodoReminderSystemEventReceiver : BroadcastReceiver() {
    @Inject lateinit var todoReminderScheduler: TodoReminderScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        // 这里不直接做重活：只负责触发 WorkManager 重排。
        todoReminderScheduler.requestReschedule()
    }
}

